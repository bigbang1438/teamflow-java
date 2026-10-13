import test from 'node:test';
import assert from 'node:assert/strict';
import * as domain from '../src/domain.js';

test('only project owners manage members; viewers cannot write tasks',()=>{
  assert.equal(domain.canManageMembers?.('owner'),true);
  assert.equal(domain.canManageMembers?.('editor'),false);
  assert.equal(domain.canManageMembers?.('viewer'),false);
  assert.equal(domain.canWriteProject?.('owner'),true);
  assert.equal(domain.canWriteProject?.('editor'),true);
  assert.equal(domain.canWriteProject?.('viewer'),false);
  assert.equal(domain.canWriteProject?.('unknown'),false);
});
test('project request paths cannot accidentally fall back to a global collection',()=>{
  assert.equal(domain.projectPath?.(12,'/tasks?status=done'),'/projects/12/tasks?status=done');
  assert.throws(()=>domain.projectPath?.(null,'/tasks'));
});
test('assignment choices exclude readers and retain real member identities',()=>{
  const members=[{username:'alice',role:'owner'},{username:'bob',role:'editor'},{username:'reader',role:'viewer'}];
  assert.deepEqual(domain.assignableMembers?.(members),members.slice(0,2));
});
test('new project and member inputs reject blanks, long names and owner escalation',()=>{
  assert.equal(domain.validateProject?.('交付工作空间'),'');
  assert.ok(domain.validateProject?.('  '));
  assert.ok(domain.validateProject?.('x'.repeat(81)));
  assert.equal(domain.validateMember?.({username:'demo_editor',role:'editor'}),'');
  assert.ok(domain.validateMember?.({username:'demo_editor',role:'owner'}));
  assert.ok(domain.validateMember?.({username:'../admin',role:'editor'}));
});
