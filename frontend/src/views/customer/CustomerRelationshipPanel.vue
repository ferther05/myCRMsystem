<!-- 文件作用：集中展示已授权客户的联系人角色、近期互动与下一步维护计划。 -->
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { contactRoles } from '@/utils/customer-rules';
import { customerRelationship, maintenanceTiming } from '@/utils/customer-relationship';

const props = defineProps({ customer: { type: Object, required: true } });
const emit = defineEmits(['contacts', 'follows', 'follow']);
const relationship = computed(() => customerRelationship(props.customer));
const writable = computed(() => props.customer.canEdit && !props.customer.readonly);
const clock = ref(Date.now());
let timer;
onMounted(() => { timer = setInterval(() => { clock.value = Date.now(); }, 60000); });
onUnmounted(() => clearInterval(timer));
const timing = computed(() => maintenanceTiming(relationship.value.plan, clock.value));
const methods = { PHONE: '电话', WECHAT: '微信', EMAIL: '邮件', VISIT: '拜访', MEETING: '会议', OTHER: '其他' };

/** 以北京时间显示到秒；缺失数据不补造。 */
function time(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '未记录'; }
/** 兼容历史联系人快照，联系人已删除时保留可用历史姓名。 */
function contactName(record) {
  return record.contactName || props.customer.contacts?.find((item) => item.id === record.contactId)?.contactName || '未记录联系人';
}
/** 清楚区分客户维护与商机跟进，避免把两者阶段效果混为一谈。 */
function source(record) {
  if (!record.opportunityId) return '客户跟进';
  const opportunity = props.customer.opportunities?.find((item) => item.id === record.opportunityId);
  return opportunity ? `商机跟进 · ${opportunity.opportunityName}` : '商机跟进';
}
/** 核验状态只陈述后端事实，不以计划是否存在判断跟进有效性。 */
function review(record) {
  if (record.reviewStatus === 'RETURNED') return '已打回';
  if (record.reviewStatus === 'PENDING') return '待核验';
  return record.effective ? (record.reviewStatus === 'CONFIRMED' ? '已核验有效' : '有效跟进') : '仅留痕';
}
</script>

<template>
  <div class="relationship-panel">
    <p class="relationship-note">围绕联系人持续维护；近期互动与计划来自现有跟进记录，不代表合同或商机条件已经完成。</p>
    <section class="maintenance-plan" aria-labelledby="maintenance-title">
      <div class="relationship-heading"><h3 id="maintenance-title">下一步维护计划</h3><span class="plan-timing">{{ timing }}</span></div>
      <template v-if="relationship.plan">
        <p class="plan-action">{{ relationship.plan.nextAction }}</p>
        <p class="plan-meta">联系 {{ contactName(relationship.plan) }} · {{ time(relationship.plan.nextActionAt) }}（北京时间）</p>
        <p class="relationship-note">记录人：{{ relationship.plan.followUserName || '未记录' }} · {{ source(relationship.plan) }} · {{ review(relationship.plan) }}</p>
        <p class="relationship-note">取最近一条未打回跟进中的安排；到时需核对进展，不自动视为完成。</p>
      </template>
      <p v-else class="empty-note">最近未打回的跟进没有完整维护计划，请在新增跟进中填写下一步动作和时间。</p>
      <el-button v-if="writable" type="primary" :disabled="!relationship.contacts.length" @click="emit('follow', relationship.plan?.contactId)">新增维护跟进</el-button>
      <p v-if="writable && !relationship.contacts.length" class="relationship-note">先维护联系人，再安排沟通。</p>
      <p v-if="customer.readonly" class="relationship-note">交接期只读，可查看维护信息。</p>
    </section>

    <div class="relationship-columns">
      <section class="relationship-section" aria-labelledby="relationship-contacts-title">
        <div class="relationship-heading"><h3 id="relationship-contacts-title">联系人角色</h3><el-button link type="primary" @click="emit('contacts')">{{ customer.canManageContacts && !customer.readonly ? '维护联系人' : '查看联系人' }}</el-button></div>
        <p class="relationship-note">共 {{ relationship.contacts.length }} 位 · 优先展示主要联系人、决策人及财务</p>
        <ul v-if="relationship.contacts.length" class="relationship-contacts">
          <li v-for="contact in relationship.contacts.slice(0, 3)" :key="contact.id">
            <div class="contact-heading"><strong>{{ contact.contactName }}</strong><el-tag v-if="contact.primary" size="small" effect="plain">主要联系人</el-tag></div>
            <p>{{ contactRoles[contact.decisionRole] || '角色未记录' }}<span v-if="contact.jobTitle"> · {{ contact.jobTitle }}</span></p>
            <p class="relationship-note">{{ contact.phone || '电话未填写' }}</p>
            <el-button v-if="writable" link type="primary" @click="emit('follow', contact.id)">记录沟通</el-button>
          </li>
        </ul>
        <p v-else class="empty-note">暂无联系人；可先记录姓名、职务与联系角色。</p>
        <el-button v-if="relationship.contacts.length > 3" link type="primary" @click="emit('contacts')">查看全部 {{ relationship.contacts.length }} 位</el-button>
      </section>

      <section class="relationship-section" aria-labelledby="relationship-interactions-title">
        <div class="relationship-heading"><h3 id="relationship-interactions-title">近期互动</h3><el-button link type="primary" @click="emit('follows')">完整时间线</el-button></div>
        <p class="relationship-note">最近三次沟通 · 包含待核验及打回记录</p>
        <ol v-if="relationship.interactions.length" class="relationship-interactions">
          <li v-for="record in relationship.interactions" :key="record.id">
            <div class="interaction-heading"><strong>{{ contactName(record) }} · {{ methods[record.followMethod] || record.followMethod || '方式未记录' }}</strong><el-tag size="small" :type="record.reviewStatus === 'RETURNED' ? 'warning' : 'info'">{{ review(record) }}</el-tag></div>
            <p class="relationship-note">{{ time(record.followedAt) }} · {{ record.followUserName || '操作人未记录' }}</p>
            <p class="interaction-result">{{ record.substantiveResult || record.content || '未记录沟通内容' }}</p>
            <p class="relationship-note">{{ source(record) }}</p>
          </li>
        </ol>
        <p v-else class="empty-note">暂无互动记录。记录一次沟通，留下结果和下一步安排。</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.relationship-panel { color: var(--crm-text-main); }
.relationship-note, .plan-meta, .empty-note { color: var(--crm-text-secondary); font-size: 13px; line-height: 1.7; margin: 8px 0; overflow-wrap: anywhere; }
.maintenance-plan { padding: 18px; margin: 14px 0; background: var(--crm-bg-page); border: 1px solid var(--crm-border); border-left: 3px solid var(--crm-color-brand); border-radius: 8px; }
.relationship-heading, .contact-heading, .interaction-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.relationship-heading h3 { font-size: 15px; margin: 0; }
.plan-timing { color: var(--crm-text-secondary); font-size: 12px; }
.plan-action { font-weight: 600; font-size: 15px; margin: 12px 0 6px; white-space: pre-wrap; overflow-wrap: anywhere; }
.relationship-columns { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); gap: 16px; }
.relationship-section { padding: 16px; border: 1px solid var(--crm-border); border-radius: 8px; min-width: 0; }
.relationship-contacts, .relationship-interactions { list-style: none; margin: 0; padding: 0; }
.relationship-contacts li, .relationship-interactions li { border-top: 1px solid var(--crm-border); padding: 14px 0; overflow-wrap: anywhere; }
.relationship-contacts li:last-child, .relationship-interactions li:last-child { padding-bottom: 0; }
.relationship-contacts p { font-size: 13px; line-height: 1.6; margin: 7px 0; }
.contact-heading strong, .interaction-heading strong { font-size: 14px; }
.interaction-result { font-size: 13px; line-height: 1.7; margin: 8px 0; white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
@media (max-width: 640px) { .relationship-columns { grid-template-columns: 1fr; } .maintenance-plan, .relationship-section { padding: 14px; } }
</style>
