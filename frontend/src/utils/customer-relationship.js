/* 文件作用：从已授权客户详情整理联系人、近期互动与最近维护计划。 */
/** 后端无偏移的业务时间按北京时间解析，不受浏览器所在时区影响。 */
export function relationshipTime(value) {
  if (!value || typeof value !== 'string') return NaN;
  const iso = value.replace(' ', 'T');
  return Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(iso) ? iso : `${iso}+08:00`);
}

/** 最近互动保留打回记录；计划只取最近未打回记录，不回退挖掘过时计划。 */
export function customerRelationship(customer) {
  const contacts = [...(customer?.contacts || [])].sort((a, b) =>
    Number(Boolean(b.primary)) - Number(Boolean(a.primary))
    || ({ DECISION_MAKER: 0, FINANCE: 1 }[a.decisionRole] ?? 2)
      - ({ DECISION_MAKER: 0, FINANCE: 1 }[b.decisionRole] ?? 2));
  const records = [...(customer?.followRecords || [])].sort((a, b) => {
    const left = relationshipTime(a.followedAt), right = relationshipTime(b.followedAt);
    return (Number.isFinite(right) ? right : 0) - (Number.isFinite(left) ? left : 0)
      || Number(b.id) - Number(a.id);
  });
  const latest = records.find((record) => record.reviewStatus !== 'RETURNED');
  const plan = latest?.nextAction?.trim() && Number.isFinite(relationshipTime(latest.nextActionAt))
    ? latest : null;
  return { contacts, interactions: records.slice(0, 3), plan };
}

/** 时间已到仅提示联系，不推定计划完成或逾期业务事实。 */
export function maintenanceTiming(plan, now) {
  if (!plan) return '尚未安排';
  return relationshipTime(plan.nextActionAt) <= now ? '时间已到，请确认进展' : '按计划联系';
}
