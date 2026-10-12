export const statuses = [
  {id:'backlog',label:'待开始',hint:'下一步值得推进的工作'},
  {id:'doing',label:'进行中',hint:'聚焦正在交付的任务'},
  {id:'blocked',label:'有阻碍',hint:'及时记录并解决卡点'},
  {id:'done',label:'已完成',hint:'让每一次交付有迹可循'}
];
export const priorities = {low:'低优先级',medium:'中优先级',high:'高优先级'};
export function allowedTransitions(status) {
  const transitions={backlog:['doing'],doing:['backlog','blocked','done'],blocked:['doing','backlog'],done:['doing']};
  return Object.hasOwn(transitions,status) ? transitions[status] : [];
}
export function groupTasks(tasks) {
  const columns={backlog:[],doing:[],blocked:[],done:[]};
  for (const task of tasks) if (Object.hasOwn(columns,task.status)) columns[task.status].push(task);
  return columns;
}
export function taskQuery({q='',status='',priority='',page=0,size=50}) {
  const query=new URLSearchParams();
  if (q.trim()) query.set('q',q.trim());
  if (status) query.set('status',status);
  if (priority) query.set('priority',priority);
  query.set('page',String(page));
  query.set('size',String(size));
  return query.toString();
}
export function toTaskPayload(form) {
  const result={title:form.title.trim(),description:(form.description ?? '').trim(),assignee:(form.assignee ?? '').trim(),priority:form.priority || 'medium',dueDate:form.dueDate || null};
  if (Number.isInteger(form.version)) result.version=form.version;
  return result;
}
export function validateTask(form) {
  if (!form.title.trim() || form.title.trim().length>120) return '任务标题需填写 1–120 个字符';
  if ((form.description ?? '').length>2000) return '任务说明不能超过 2000 个字符';
  if ((form.assignee ?? '').trim().length>60) return '负责人不能超过 60 个字符';
  if (!Object.hasOwn(priorities,form.priority)) return '请选择有效的优先级';
  return '';
}
export function errorInfo(error) {
  if (error?.status===409 && error.code==='version_conflict') return {conflict:true,message:'任务已被其他操作更新，请刷新后再试，避免覆盖最新内容。'};
  if (!error?.status) return {conflict:false,message:'暂时无法连接服务，请确认后端已启动，再重新加载。'};
  return {conflict:false,message:error.message || '操作未完成，请稍后重试。'};
}
export function isOverdue(task,today) {
  return !!task.dueDate && task.status!=='done' && task.dueDate<today;
}
export const utcDay = (date=new Date()) => date.toISOString().slice(0,10);
