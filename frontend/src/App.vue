<script setup>
import {computed,nextTick,onBeforeUnmount,onMounted,reactive,ref,watch} from 'vue';
import {request,refreshSession,login,logout} from './api.js';
import {allowedTransitions,errorInfo,groupTasks,isOverdue,priorities,statuses,taskQuery,toTaskPayload,validateTask,utcDay,canWriteProject,canManageMembers,assignableMembers,projectPath,validateProject,validateMember} from './domain.js';

const user=ref(null),booting=ref(true),loginBusy=ref(false),loginError=ref(null),auth=reactive({username:'demo_owner',password:''});
const projects=ref([]),projectId=ref(null),members=ref([]),projectAudit=ref([]),view=ref('board');
const activeProject=computed(()=>projects.value.find(p=>p.id===projectId.value));
const writable=computed(()=>canWriteProject(activeProject.value?.role));
const managesMembers=computed(()=>canManageMembers(activeProject.value?.role));
const assignees=computed(()=>assignableMembers(members.value));
const roleLabel=role=>({owner:'项目负责人',editor:'编辑者',viewer:'只读成员'})[role] || role;
const projectDialog=ref(null),projectName=ref(''),projectError=ref(null),projectSaving=ref(false);
const memberForm=reactive({username:'demo_editor',role:'editor'}),memberError=ref(null),memberSaving=ref(false);
const commentText=ref(''),comments=ref([]),commentSaving=ref(false),commentError=ref(null);
const demoAccounts=['demo_owner','demo_editor','demo_viewer','demo_guest'];
async function projectRequest(path,options) {return request(projectPath(projectId.value,path),options);}
function sessionFailure(failure) {
  if (failure.status===401) {user.value=null;projects.value=[];projectId.value=null;panelTask.value=null;dialog.value?.close();announce('登录已过期，请重新登录。');}
  return errorInfo(failure);
}
async function loadProjects(selectId=projectId.value) {
  const result=await request('/projects');projects.value=result.data;
  projectId.value=projects.value.some(p=>p.id===selectId) ? selectId : projects.value[0]?.id ?? null;
  if (projectId.value) await load(); else {tasks.value=[];members.value=[];summary.value=null;loading.value=false;}
}
async function initialize() {
  booting.value=true;
  try {user.value=await refreshSession();if (user.value) await loadProjects();}
  catch(failure) {loginError.value=errorInfo(failure);}
  finally {booting.value=false;}
}
async function signIn() {
  loginBusy.value=true;loginError.value=null;
  try {user.value=await login(auth.username,auth.password);await loadProjects();}
  catch(failure) {loginError.value=errorInfo(failure);}
  finally {auth.password='';loginBusy.value=false;}
}
async function signOut() {
  try {await logout();user.value=null;projects.value=[];projectId.value=null;tasks.value=[];summary.value=null;panelTask.value=null;view.value='board';announce('已安全退出。');}
  catch(failure) {error.value=sessionFailure(failure);}
}
function selectProject() {requestId++;activityId++;panelTask.value=null;view.value='board';page.value=0;clearFilters();load();}
async function openProjectDialog() {projectName.value='';projectError.value=null;await nextTick();projectDialog.value.showModal();}
async function createProject() {
  const invalid=validateProject(projectName.value);if (invalid) {projectError.value={message:invalid};return;}
  projectSaving.value=true;
  try {const result=await request('/projects',{method:'POST',body:JSON.stringify({name:projectName.value.trim()})});projectDialog.value.close();await loadProjects(result.data.id);view.value='members';announce('项目已创建，可以添加协作成员。');}
  catch(failure) {projectError.value=sessionFailure(failure);}
  finally {projectSaving.value=false;}
}
async function inviteMember() {
  const invalid=validateMember(memberForm);if (invalid) {memberError.value={message:invalid};return;}
  memberSaving.value=true;memberError.value=null;
  try {await projectRequest('/members',{method:'POST',body:JSON.stringify(memberForm)});await load();announce('成员已加入当前项目。');}
  catch(failure) {memberError.value=sessionFailure(failure);}
  finally {memberSaving.value=false;}
}
async function changeRole(member,role) {
  memberSaving.value=true;memberError.value=null;
  try {await projectRequest(`/members/${member.username}`,{method:'PUT',body:JSON.stringify({role})});await load();announce('成员角色已更新；只读成员的任务分派已清除。');}
  catch(failure) {memberError.value=sessionFailure(failure);}
  finally {memberSaving.value=false;}
}
async function addComment() {
  if (!commentText.value.trim()) {commentError.value={message:'评论不能为空'};return;}
  commentSaving.value=true;commentError.value=null;
  try {await projectRequest(`/tasks/${panelTask.value.id}/comments`,{method:'POST',body:JSON.stringify({body:commentText.value.trim()})});commentText.value='';await openActivity(panelTask.value);await load();announce('评论已保存并记录操作者。');}
  catch(failure) {commentError.value=sessionFailure(failure);}
  finally {commentSaving.value=false;}
}

const tasks=ref([]),summary=ref(null),loading=ref(true),error=ref(null),notice=ref('');
const filters=reactive({q:'',status:'',priority:''});
const page=ref(0),meta=ref({total:0,totalPages:0,size:50}),busy=ref(false),demoBusy=ref(false);
const columns=computed(()=>groupTasks(tasks.value));
const dialog=ref(null),editing=ref(null),formError=ref(null),saving=ref(false);
const form=reactive({title:'',description:'',assignee:'',priority:'medium',dueDate:''});
const panelTask=ref(null),activities=ref([]),activityLoading=ref(false),activityError=ref(null),panelHeading=ref(null);
let requestId=0,activityId=0,searchTimer,noticeTimer,lastFocus;
const today=ref(utcDay());
const stateLabel=(id)=>statuses.find(item=>item.id===id)?.label ?? id;
const dateLabel=(value)=>value ? new Intl.DateTimeFormat('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(value)) : '—';
const actionLabel=(action)=>({CREATED:'创建任务',UPDATED:'更新内容',STATUS_CHANGED:'状态变更',created:'创建任务',updated:'更新内容',status_changed:'状态变更'})[action] ?? action;
function announce(message) {notice.value=message;clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>{notice.value='';},5000);}

async function load() {
  today.value=utcDay();
  const id=++requestId;loading.value=true;error.value=null;
  try {
    if (!projectId.value) {loading.value=false;return;}
    const [list,stats,people,audit]=await Promise.all([projectRequest(`/tasks?${taskQuery({...filters,page:page.value,size:50})}`),projectRequest('/summary'),projectRequest('/members'),projectRequest('/activities')]);
    if (id!==requestId) return;
    tasks.value=list.data;meta.value=list.meta;summary.value=stats.data;members.value=people.data;projectAudit.value=audit.data;
    const currentMember=people.data.find(member=>member.username===user.value?.username);
    if (activeProject.value && currentMember) activeProject.value.role=currentMember.role;
    if (page.value>0 && page.value>=list.meta.totalPages) {page.value=Math.max(0,list.meta.totalPages-1);return load();}
  } catch (failure) {if (id===requestId) error.value=sessionFailure(failure);}
  finally {if (id===requestId) loading.value=false;}
}
watch(()=>[filters.q,filters.status,filters.priority],()=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{page.value=0;if (user.value && projectId.value) load();},250);});
function changePage(amount) {page.value+=amount;load();}
function clearFilters() {filters.q='';filters.status='';filters.priority='';}
function closeEditor() {if (saving.value) return;dialog.value.close();formError.value=null;lastFocus?.focus();}
async function openEditor(task,event) {
  if (!writable.value) return openActivity(task,event);
  lastFocus=event?.currentTarget;editing.value=task ? {...task} : null;
  Object.assign(form,{title:task?.title ?? '',description:task?.description ?? '',assignee:task?.assignee ?? '',priority:task?.priority ?? 'medium',dueDate:task?.dueDate ?? ''});
  delete form.version;if (task) form.version=task.version;
  formError.value=null;await nextTick();dialog.value.showModal();
}
async function refreshEditor() {
  saving.value=true;
  try {const {data}=await projectRequest(`/tasks/${editing.value.id}`);editing.value=data;Object.assign(form,{title:data.title,description:data.description ?? '',assignee:data.assignee ?? '',priority:data.priority,dueDate:data.dueDate ?? '',version:data.version});formError.value=null;announce('已加载最新任务内容，请确认后保存。');}
  catch (failure) {formError.value=sessionFailure(failure);}
  finally {saving.value=false;}
}
async function saveTask() {
  const invalid=validateTask(form);if (invalid) {formError.value={message:invalid};return;}
  saving.value=true;formError.value=null;
  try {
    await projectRequest(editing.value ? `/tasks/${editing.value.id}` : '/tasks',{method:editing.value ? 'PUT' : 'POST',body:JSON.stringify(toTaskPayload(form))});
    dialog.value.close();lastFocus?.focus();announce(editing.value ? '任务内容已保存。' : '新任务已创建。');await load();
  } catch (failure) {formError.value=sessionFailure(failure);}
  finally {saving.value=false;}
}
async function move(task,status) {
  if (!writable.value || busy.value || !allowedTransitions(task.status).includes(status)) return;
  busy.value=true;error.value=null;
  try {await projectRequest(`/tasks/${task.id}/status`,{method:'PATCH',body:JSON.stringify({status,version:task.version})});announce(`任务已移至${stateLabel(status)}。`);await load();if (panelTask.value?.id===task.id) await openActivity({...task,status});}
  catch (failure) {error.value=sessionFailure(failure);}
  finally {busy.value=false;}
}
async function openActivity(task,event) {
  if (event) lastFocus=event.currentTarget;
  const id=++activityId;panelTask.value=task;activities.value=[];comments.value=[];activityLoading.value=true;activityError.value=null;commentError.value=null;
  await nextTick();panelHeading.value?.focus();
  try {const [result,discussion]=await Promise.all([projectRequest(`/tasks/${task.id}/activities`),projectRequest(`/tasks/${task.id}/comments`)]);if (id===activityId) {activities.value=result.data;comments.value=discussion.data;}}
  catch (failure) {if (id===activityId) activityError.value=sessionFailure(failure);}
  finally {if (id===activityId) activityLoading.value=false;}
}
function closeActivity() {activityId++;panelTask.value=null;lastFocus?.focus();}
function onEscape(event) {if (event.key==='Escape' && !dialog.value?.open && panelTask.value) closeActivity();}
async function addDemo() {
  demoBusy.value=true;error.value=null;let created=0;
  const examples=[
    {title:'[演示] 整理接口需求',description:'示例任务：明确字段、验收标准与交付边界。可编辑为自己的工作内容。',assignee:user.value.username,priority:'medium',state:'backlog'},
    {title:'[演示] 完成看板交互',description:'示例任务：验证新建、筛选与状态流转。此条数据不代表实际项目进度。',assignee:user.value.username,priority:'high',state:'doing'},
    {title:'[演示] 确认联调时间',description:'示例阻碍：等待协作方反馈时间，确认后移回进行中。',assignee:user.value.username,priority:'high',state:'blocked'},
    {title:'[演示] 补充启动说明',description:'示例任务：记录本地启动步骤，便于团队成员复现。',assignee:user.value.username,priority:'low',state:'done'}
  ];
  try {
    for (const example of examples) {
      let {data}=await projectRequest('/tasks',{method:'POST',body:JSON.stringify(toTaskPayload({...example,dueDate:''}))});created++;
      if (example.state!=='backlog') {
        ({data}=await projectRequest(`/tasks/${data.id}/status`,{method:'PATCH',body:JSON.stringify({status:'doing',version:data.version})}));
        if (example.state!=='doing') await projectRequest(`/tasks/${data.id}/status`,{method:'PATCH',body:JSON.stringify({status:example.state,version:data.version})});
      }
    }
    announce('已添加 4 条演示任务，可以直接编辑。');
  } catch (failure) {announce(`已创建 ${created} 条演示任务，未完成的部分可稍后补充。`);error.value=errorInfo(failure);}
  finally {demoBusy.value=false;const failure=error.value;await load();if (failure) error.value=failure;}
}
onMounted(()=>{initialize();document.addEventListener('keydown',onEscape);});
onBeforeUnmount(()=>{requestId++;activityId++;clearTimeout(searchTimer);clearTimeout(noticeTimer);document.removeEventListener('keydown',onEscape);});
</script>

<template>
  <main v-if="booting" class="login-screen"><p role="status">正在恢复会话…</p></main>
  <main v-else-if="!user" class="login-screen">
    <section class="login-copy"><div class="brand"><span class="brand-mark" aria-hidden="true">≋</span>TeamFlow</div><h1>每项交付，<br>都有清楚的归属。</h1><p>把项目成员、任务进展与讨论放在同一个协作空间。登录后，只能看到你参与的项目。</p><div class="login-rule"></div><small>本机学习作品 · 使用虚拟演示账号</small></section>
    <form class="login-form" @submit.prevent="signIn"><h2>登录工作空间</h2><p>密码由首次启动时的本机配置提供。</p><label class="form-label">账号<select v-model="auth.username" :disabled="loginBusy"><option v-for="account in demoAccounts" :key="account" :value="account">{{ account }}</option></select></label><label class="form-label">密码<input v-model="auth.password" type="password" autocomplete="current-password" required maxlength="72" :disabled="loginBusy"></label><div v-if="loginError" class="form-error" role="alert">{{ loginError.message }}<button v-if="!loginError.status" class="secondary" type="button" @click="initialize">重新连接</button></div><button class="primary" :disabled="loginBusy">{{ loginBusy ? '正在登录…' : '登录' }}</button><p class="login-hint">演示项目默认提供负责人、编辑者和只读成员三种角色。外部账号只会看到自己创建或加入的项目。</p></form>
  </main>
  <template v-else>
  <a class="skip-link" href="#main">跳到任务看板</a>
  <div class="workspace">
    <aside class="sidebar" aria-label="工作空间导航">
      <a class="brand" href="#main" aria-label="TeamFlow 首页"><span class="brand-mark" aria-hidden="true">≋</span>TeamFlow</a>
      <div class="workspace-label"><span class="workspace-icon" aria-hidden="true">T</span><div><strong>我的项目</strong><small>{{ projects.length }} 个参与项目</small></div></div>
      <label class="project-select">当前项目<select v-model="projectId" @change="selectProject"><option v-if="!projects.length" :value="null">暂无项目</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option></select></label>
      <button class="secondary new-project" @click="openProjectDialog">＋ 新建项目</button>
      <button class="nav-item" :class="{active:view==='board'}" @click="view='board'"><span aria-hidden="true">▦</span>任务看板</button>
      <button class="nav-item" :class="{active:view==='members'}" @click="view='members'"><span aria-hidden="true">♧</span>项目成员</button>
      <button class="nav-item" :class="{active:view==='audit'}" @click="view='audit'"><span aria-hidden="true">≡</span>项目活动</button>
      <div class="sidebar-note"><span class="note-rule"></span><p>把任务拆清楚，<br>把进展看明白。</p><small>每次更新都会记录在活动中。</small></div>
    </aside>
    <main id="main" class="main" tabindex="-1">
      <div class="session-bar"><p>{{ user.username }} <span v-if="activeProject" class="role-badge">{{ roleLabel(activeProject.role) }}</span></p><button class="quiet" @click="signOut">退出登录</button></div>
      <header class="page-header"><div><p class="breadcrumb">{{ activeProject?.name || '我的项目' }} / 任务协作</p><h1>{{ view==='board' ? '任务看板' : view==='members' ? '项目成员' : '项目活动' }}</h1><p class="page-description">{{ view==='board' ? '从想法到交付，跟进每一个行动。' : view==='members' ? '清楚的权限，让协作有边界。' : '查看当前项目的任务、讨论与成员变化。' }}</p></div><button v-if="projectId && writable && view==='board'" class="primary" @click="openEditor(null,$event)"><span aria-hidden="true">＋</span> 新建任务</button></header>
      <div v-if="!projectId" class="empty-workspace"><h2>还没有参与的项目</h2><p>创建项目后，你将成为负责人，并可添加现有演示账号。</p><button class="primary" @click="openProjectDialog">新建项目</button></div>
      <template v-else-if="view==='board'">
      <p v-if="!writable" class="readonly-note">当前是只读角色，可以查看任务和讨论；编辑操作由项目负责人或编辑者完成。</p>
      <section class="summary" aria-label="工作空间任务统计">
        <div><span>全部任务</span><strong>{{ summary?.total ?? '—' }}</strong><small>工作空间总览</small></div>
        <div><span>正在推进</span><strong>{{ summary?.byStatus?.doing ?? '—' }}</strong><small>保持专注，逐项交付</small></div>
        <div><span>已逾期</span><strong :class="{'warning-number':summary?.overdue>0}">{{ summary?.overdue ?? '—' }}</strong><small>待跟进的到期工作</small></div>
        <div class="completion"><span>完成比例</span><strong>{{ summary ? `${Math.round(summary.completionRate)}%` : '—' }}</strong><progress class="meter" max="100" :value="summary?.completionRate ?? 0" aria-label="任务完成比例"></progress></div>
      </section>
      <form class="filters" role="search" @submit.prevent="load"><label class="search-field"><span class="sr-only">搜索任务标题、说明或负责人</span><span aria-hidden="true">⌕</span><input v-model="filters.q" maxlength="120" placeholder="搜索标题、说明或负责人" type="search"></label><label><span class="sr-only">按状态筛选</span><select v-model="filters.status"><option value="">所有状态</option><option v-for="item in statuses" :key="item.id" :value="item.id">{{ item.label }}</option></select></label><label><span class="sr-only">按优先级筛选</span><select v-model="filters.priority"><option value="">所有优先级</option><option v-for="(label,id) in priorities" :key="id" :value="id">{{ label }}</option></select></label><button class="quiet refresh" type="button" :disabled="loading" @click="load"><span aria-hidden="true">↻</span> 刷新</button></form>
      <div v-if="error" class="error-banner" role="alert"><p>{{ error.message }}</p><button class="quiet" @click="load">重新加载</button></div>
      <div class="board-caption"><span>本页 {{ tasks.length }} 条<span v-if="meta.total">，筛选结果共 {{ meta.total }} 条</span></span><span class="board-tip">状态操作仅展示可用的下一步</span></div>
      <div v-if="loading" class="loading-state" role="status"><span class="spinner" aria-hidden="true"></span>正在加载任务…</div>
      <section v-else-if="!tasks.length && !error" class="empty-workspace"><div class="empty-symbol" aria-hidden="true">▤</div><h2>{{ filters.q || filters.status || filters.priority ? '没有符合条件的任务' : '从第一个任务开始' }}</h2><p>{{ filters.q || filters.status || filters.priority ? '换个关键词，或清除筛选后再查看。' : writable ? '创建真实任务，或添加明确标记的示例来体验看板。' : '等待项目负责人或编辑者创建任务。' }}</p><div class="empty-actions"><button v-if="filters.q || filters.status || filters.priority" class="primary" @click="clearFilters">清除筛选</button><template v-else-if="writable"><button class="primary" @click="openEditor(null,$event)">新建任务</button><button class="secondary" :disabled="demoBusy" @click="addDemo">{{ demoBusy ? '正在创建…' : '添加演示任务' }}</button></template></div></section>
      <section v-else class="board" aria-label="任务看板" :aria-busy="busy">
        <section v-for="column in statuses" :key="column.id" class="column" :class="column.id" :aria-label="column.label">
          <header class="column-header"><div><span class="status-dot" aria-hidden="true"></span><h2>{{ column.label }}</h2><span class="count">{{ columns[column.id].length }}</span></div><p>{{ column.hint }}</p></header>
          <div v-if="!columns[column.id].length" class="empty-column">暂无{{ column.label }}任务</div>
          <article v-for="task in columns[column.id]" :key="task.id" class="task-card">
            <div class="task-topline"><span class="priority" :class="task.priority">{{ priorities[task.priority] }}</span><span class="task-id">#{{ task.id }}</span></div>
            <button class="task-title" @click="openEditor(task,$event)">{{ task.title }}</button><p v-if="task.description" class="task-description">{{ task.description }}</p>
            <div class="task-meta"><span class="assignee"><span class="avatar" aria-hidden="true">{{ task.assignee?.slice(0,1) || '·' }}</span>{{ task.assignee || '未分配' }}</span><span v-if="task.dueDate" class="due-date" :class="{overdue:isOverdue(task,today)}">{{ task.dueDate.slice(5) }}<span v-if="isOverdue(task,today)"> 逾期</span></span></div>
            <div class="task-actions"><label><span class="sr-only">将“{{ task.title }}”移至</span><select :value="task.status" :disabled="busy || !writable" @change="move(task,$event.target.value); $event.target.value=task.status"><option :value="task.status" disabled>{{ writable ? '更改状态' : '只读' }}</option><option v-for="id in allowedTransitions(task.status)" :key="id" :value="id">{{ stateLabel(id) }}</option></select></label><button class="activity-button" :aria-label="`查看 ${task.title} 的活动记录`" @click="openActivity(task,$event)">讨论 / 记录</button></div>
          </article>
        </section>
      </section>
      <footer class="pagination"><p>看板展示当前页任务，统计仅涵盖当前项目。</p><div v-if="meta.totalPages>1"><button class="secondary" :disabled="page===0 || loading" @click="changePage(-1)">上一页</button><span>第 {{ page+1 }} / {{ meta.totalPages }} 页</span><button class="secondary" :disabled="page+1>=meta.totalPages || loading" @click="changePage(1)">下一页</button></div></footer>
      </template>
      <section v-else-if="view==='members'" class="member-page">
        <p class="section-explanation">负责人管理成员，编辑者维护任务与评论，只读成员查看项目内容。成员权限由服务端实时检查。</p>
        <form v-if="managesMembers" class="member-invite" @submit.prevent="inviteMember"><label class="form-label">添加现有账号<select v-model="memberForm.username" :disabled="memberSaving"><option v-for="account in demoAccounts" :key="account" :value="account">{{ account }}</option></select></label><label class="form-label">项目角色<select v-model="memberForm.role" :disabled="memberSaving"><option value="editor">编辑者</option><option value="viewer">只读成员</option></select></label><button class="primary" :disabled="memberSaving">添加成员</button></form>
        <div v-if="memberError || error" class="form-error" role="alert">{{ (memberError || error).message }}</div>
        <ul class="member-list"><li v-for="member in members" :key="member.id"><span class="avatar">{{ member.displayName.slice(0,1) }}</span><div><strong>{{ member.displayName }}</strong><small>{{ member.username }}</small></div><label v-if="managesMembers && member.role!=='owner'"><span class="sr-only">修改 {{ member.username }} 的角色</span><select :value="member.role" :disabled="memberSaving" @change="changeRole(member,$event.target.value)"><option value="editor">编辑者</option><option value="viewer">只读成员</option></select></label><span v-else class="role-badge">{{ roleLabel(member.role) }}</span></li></ul>
        <p v-if="managesMembers" class="field-note">改为只读角色时，该成员的任务负责人分派将自动清除，任务版本和活动记录同步更新。</p>
      </section>
      <section v-else class="project-audit"><div v-if="error" class="form-error" role="alert">{{ error.message }}</div><p class="section-explanation">最近 100 条活动，包含实际登录账号与时间。只展示当前项目的记录。</p><ol v-if="projectAudit.length" class="audit-list"><li v-for="item in projectAudit" :key="item.id"><strong>{{ item.actor }}</strong><p>{{ item.detail }} <span v-if="item.taskId">· 任务 #{{ item.taskId }}</span></p><time :datetime="item.createdAt">{{ dateLabel(item.createdAt) }}</time></li></ol><p v-else class="panel-status">项目还没有活动。</p></section>
    </main>
  </div>
  <div v-if="notice" class="toast" role="status">{{ notice }}</div>
  <dialog ref="dialog" class="task-dialog" aria-labelledby="editor-title" @cancel="saving ? $event.preventDefault() : closeEditor()">
    <form @submit.prevent="saveTask"><header class="dialog-header"><div><h2 id="editor-title">{{ editing ? '编辑任务' : '新建任务' }}</h2><p>{{ editing ? '修改任务信息，更新将记入活动。' : '写清楚要交付什么，让团队知道下一步。' }}</p></div><button type="button" class="close-button" aria-label="关闭编辑窗口" :disabled="saving" @click="closeEditor">×</button></header>
      <label class="form-label">任务标题 <span class="required">*</span><input v-model="form.title" autofocus required maxlength="120" placeholder="例如：完成账号设置页" :disabled="saving"></label>
      <label class="form-label">任务说明<textarea v-model="form.description" rows="4" maxlength="2000" placeholder="目标、具体步骤与验收标准" :disabled="saving"></textarea><span class="field-note">{{ form.description.length }} / 2000</span></label>
      <div class="form-grid"><label class="form-label">负责人<select v-model="form.assignee" :disabled="saving"><option value="">暂不分配</option><option v-for="member in assignees" :key="member.id" :value="member.username">{{ member.displayName }}（{{ member.username }}）</option></select></label><label class="form-label">优先级<select v-model="form.priority" :disabled="saving"><option v-for="(label,id) in priorities" :key="id" :value="id">{{ label }}</option></select></label></div>
      <label class="form-label">截止日期<input v-model="form.dueDate" type="date" :disabled="saving"><span class="field-note">可留空；未完成且早于今天的任务会标记为逾期。</span></label>
      <div v-if="formError" class="form-error" role="alert"><p>{{ formError.message }}</p><button v-if="formError.conflict" type="button" class="secondary" :disabled="saving" @click="refreshEditor">加载最新内容</button></div>
      <footer class="dialog-footer"><button type="button" class="secondary" :disabled="saving" @click="closeEditor">取消</button><button class="primary" :disabled="saving">{{ saving ? '正在保存…' : editing ? '保存修改' : '创建任务' }}</button></footer>
    </form>
  </dialog>
  <aside v-if="panelTask" class="activity-panel" aria-labelledby="activity-title"><header class="panel-header"><div><h2 id="activity-title" ref="panelHeading" tabindex="-1">讨论与记录</h2><p>{{ panelTask.title }}</p></div><button class="close-button" aria-label="关闭活动记录" @click="closeActivity">×</button></header><p class="activity-context">{{ stateLabel(panelTask.status) }} · 任务 #{{ panelTask.id }}</p><div v-if="activityLoading" role="status" class="panel-status">正在加载记录…</div><div v-else-if="activityError" class="form-error" role="alert"><p>{{ activityError.message }}</p><button class="secondary" @click="openActivity(panelTask)">重试</button></div><template v-else><h3>任务评论</h3><form v-if="writable" @submit.prevent="addComment" class="comment-form"><label class="form-label">新增评论<textarea v-model="commentText" rows="3" maxlength="1000" :disabled="commentSaving" placeholder="同步进展、讨论验收条件"></textarea></label><div v-if="commentError" class="form-error" role="alert">{{ commentError.message }}</div><button class="primary" :disabled="commentSaving">发布评论</button></form><ul class="comment-list"><li v-for="item in comments" :key="item.id"><strong>{{ item.actor }}</strong><p>{{ item.body }}</p><time :datetime="item.createdAt">{{ dateLabel(item.createdAt) }}</time></li></ul><p v-if="!comments.length" class="panel-status">暂无评论。</p><h3>活动记录</h3><ol v-if="activities.length" class="timeline"><li v-for="item in activities" :key="item.id"><div class="timeline-dot" aria-hidden="true"></div><strong>{{ actionLabel(item.action) }} · {{ item.actor }}</strong><p>{{ item.detail }}</p><time :datetime="item.createdAt">{{ dateLabel(item.createdAt) }}</time></li></ol><p v-else class="panel-status">暂无活动记录。</p></template></aside>
  <dialog ref="projectDialog" class="task-dialog" aria-labelledby="project-title" @cancel="projectSaving && $event.preventDefault()"><form @submit.prevent="createProject"><h2 id="project-title">新建项目</h2><label class="form-label">项目名称<input v-model="projectName" autofocus maxlength="80" required :disabled="projectSaving"></label><p class="field-note">创建后，你是项目负责人。不同项目的任务、成员和统计独立保存。</p><div v-if="projectError" class="form-error" role="alert">{{ projectError.message }}</div><footer class="dialog-footer"><button type="button" class="secondary" :disabled="projectSaving" @click="projectDialog.close()">取消</button><button class="primary" :disabled="projectSaving">创建项目</button></footer></form></dialog>
  </template>
</template>
