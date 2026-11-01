"""按用户授权新增华东协作账号及六组演示客户；通过业务接口推进四条私海商机。"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "output/work/east-collaboration-demo-20261030"
MANIFEST = WORK / "manifest.json"
BATCH = "CRM-DEMO-EAST-COLLAB-20261030"
KEYWORD = "星槎演示"
BASE = "http://127.0.0.1:8080"
MYSQL = Path("C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe")
CREDS = ROOT / "database/east-collaborator-local-credentials.md"
SCENARIOS = [
    ("public01", "公海01·琉璃鲸科技", "PUBLIC", "TECH", "WATCH", 0),
    ("public02", "公海02·月影纸鸢", "PUBLIC", "MEDIA", "NORMAL", 0),
    ("quote01", "报价01·赤曜机械", "QUOTED", "MANUFACTURING", "KEY", 28600),
    ("quote02", "报价02·蓝焰云仓", "QUOTED", "LOGISTICS", "NORMAL", 46800),
    ("deal01", "待核签01·紫电星港", "DEAL", "TECH", "KEY", 68800),
    ("deal02", "待核签02·白鹿云庭", "DEAL", "SERVICE", "NORMAL", 92800),
]


def now() -> datetime:
    """统一使用北京时间，HTTP日期时间不携带时区后缀。"""
    return datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None)


def local_value(config: str, env: str) -> str:
    """仅在内存读取本地忽略配置，不输出数据库密码或JWT签名密钥。"""
    value = os.environ.get(env)
    if not value:
        match = re.search(r"\$\{" + re.escape(env) + r":([^}]+)\}", config)
        value = match.group(1) if match else None
    if not value:
        raise RuntimeError(f"缺少本地配置 {env}")
    return value


class Seed:
    """备份、接口写入、断点记录和只读核验均限定于本批演示数据。"""
    def __init__(self):
        config = (ROOT / "backend/config/application-local.yml").read_text(encoding="utf-8-sig")
        self.env = os.environ.copy()
        self.env["MYSQL_PWD"] = local_value(config, "CRM_DB_PASSWORD")
        self.key = base64.b64decode(local_value(config, "CRM_JWT_SECRET"))
        self.state = json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {
            "batch": BATCH, "scenarios": {}, "createdAt": now().isoformat(timespec="seconds")
        }
        rows = self.rows("SELECT JSON_OBJECT('id',id,'username',username,'name',real_name,'role',role_code,"
                         "'team',team_id,'status',account_status,'mustChange',must_change_password,'version',version) "
                         "FROM sys_user WHERE username IN ('admin','east.sales','east.manager','east.collaborator')")
        self.users = {u["username"]: u for u in rows}
        for name, role in [("admin", "ADMIN"), ("east.manager", "MANAGER"), ("east.sales", "SPECIALIST")]:
            u = self.users[name]
            assert u["role"] == role and u["status"] == "ACTIVE" and not u["mustChange"], name
        self.team = self.users["east.sales"]["team"]
        assert self.team == self.users["east.manager"]["team"]
        assert self.rows("SELECT JSON_OBJECT('code',team_code,'manager',manager_id) FROM sys_team "
                         f"WHERE id={self.team} AND enabled=1")[0] == {
                             "code": "EAST", "manager": self.users["east.manager"]["id"]}

    def sql(self, statement: str) -> str:
        """使用应用账号；密码只放子进程环境，不出现在命令行或输出中。"""
        result = subprocess.run([str(MYSQL), "--protocol=TCP", "-h", "127.0.0.1", "-u", "crm_app",
            "--database=crm_system", "--default-character-set=utf8mb4", "--batch", "--raw",
            "--skip-column-names", "--execute", statement], env=self.env,
            capture_output=True, encoding="utf-8", check=True)
        return result.stdout.strip()

    def rows(self, statement: str) -> list:
        """逐行解析只读查询返回的JSON对象。"""
        return [json.loads(line) for line in self.sql(statement).splitlines() if line]

    def save(self):
        """保存非敏感ID清单，重复执行不覆盖用户已测试修改的场景。"""
        WORK.mkdir(parents=True, exist_ok=True)
        MANIFEST.write_text(json.dumps(self.state, ensure_ascii=False, indent=2), encoding="utf-8")

    def token(self, actor: str) -> str:
        """为本机授权初始化签发10分钟令牌，格式与项目JwtTokenService一致，不落盘。"""
        u = self.users[actor]
        encode = lambda x: base64.urlsafe_b64encode(x).rstrip(b"=")
        header = encode(b'{"alg":"HS256"}')
        timestamp = int(time.time())
        claims = {"iss": "crm-backend", "sub": str(u["id"]), "jti": str(uuid.uuid4()),
                  "iat": timestamp, "exp": timestamp + 600, "ver": u["version"],
                  "role": u["role"], "team": u["team"]}
        payload = encode(json.dumps(claims, separators=(",", ":")).encode())
        body = header + b"." + payload
        return (body + b"." + encode(hmac.new(self.key, body, hashlib.sha256).digest())).decode()

    def api(self, method: str, path: str, actor: str = "admin", body=None,
            raw: bytes | None = None, content_type: str = "application/json", expected: int = 200):
        """调用真实业务接口，保留归属计数、状态门槛、版本、审计与缓存处理。"""
        data = raw if raw is not None else json.dumps(body, ensure_ascii=False).encode() if body is not None else None
        request = urllib.request.Request(BASE + path, data=data, method=method,
            headers={"Authorization": "Bearer " + self.token(actor),
                     "Content-Type": "application/octet-stream" if content_type == "download" else content_type})
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                result = response.read()
                assert response.status == expected
                if content_type == "download":
                    return result
                payload = json.loads(result)
                assert payload["code"] == 0, payload.get("message")
                return payload["data"]
        except urllib.error.HTTPError as error:
            if error.code == expected:
                return None
            payload = json.loads(error.read())
            raise RuntimeError(f"{method} {path}: HTTP{error.code} code={payload.get('code')} "
                               f"{payload.get('message')} {payload.get('data')}") from None

    def snapshot(self, table: str, max_id: int) -> str:
        """只对新增前已有记录计算摘要，核对用户原有业务数据未被改写。"""
        data = self.sql(f"SELECT * FROM {table} WHERE id<={int(max_id)} ORDER BY id")
        return hashlib.sha256(data.encode()).hexdigest()

    def backup(self):
        """首次写入前备份整库，并记录已有客户、商机、合同及附件摘要。"""
        if self.state.get("backup"):
            return
        target = ROOT / "output/database-backups" / ("before-east-collaboration-" + now().strftime("%Y%m%d-%H%M%S") + ".sql")
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(MYSQL.with_name("mysqldump.exe")), "--protocol=TCP", "-h", "127.0.0.1",
            "-u", "crm_app", "--single-transaction", "--no-tablespaces", "--skip-lock-tables",
            "--set-gtid-purged=OFF", "--default-character-set=utf8mb4", "--result-file=" + str(target),
            "crm_system"], env=self.env, capture_output=True, check=True)
        baseline = {}
        for table in ["crm_customer", "crm_opportunity", "crm_contract", "crm_follow_record", "crm_attachment"]:
            maximum = int(self.sql(f"SELECT COALESCE(MAX(id),0) FROM {table}"))
            baseline[table] = {"maxId": maximum, "sha256": self.snapshot(table, maximum)}
        self.state["baseline"] = baseline
        self.state["backup"] = str(target)
        self.save()

    def account(self):
        """创建华东协作人；已存在时仅核对归属，不重置密码或清空账号。"""
        existing = self.users.get("east.collaborator")
        if existing:
            assert existing["name"] == "华东协作人" and existing["role"] == "SPECIALIST"
            assert existing["team"] == self.team and existing["status"] == "ACTIVE"
            self.state["collaboratorId"] = existing["id"]
            self.save()
            return
        assert self.sql("SELECT COUNT(*) FROM sys_user WHERE real_name='华东协作人'") == "0"
        password = "East!" + secrets.token_hex(5) + "2026"
        # 先落在Git忽略的本地资料，接口成功后即可找回；不打印明文密码。
        CREDS.write_text("# 华东协作人本地演示账号\n\n"
            "- 姓名：华东协作人\n- 用户名：`east.collaborator`\n"
            f"- 初始密码：`{password}`\n- 团队：华东销售团队；经理：华东经理（east.manager）\n"
            "- 角色：专员；首次登录强制修改密码。\n"
            "- 由本次演示初始化创建；本文件被Git忽略，请勿上传。\n", encoding="utf-8")
        created = self.api("POST", "/api/users", body={"username": "east.collaborator", "realName": "华东协作人",
            "roleCode": "SPECIALIST", "teamId": self.team, "initialPassword": password})
        assert created["mustChangePassword"] and created["teamId"] == self.team
        self.state["collaboratorId"] = created["id"]
        self.save()

    def customer(self, key: str, label: str, target: str, industry: str, level: str, index: int) -> dict:
        """只创建指定批次的客户；公海不挂商机，后四个客户直接分配east.sales。"""
        entry = self.state["scenarios"].setdefault(key, {"name": KEYWORD + "·" + label, "target": target})
        if "customerId" not in entry:
            name = entry["name"]
            # 名称固定且只包含本脚本中的中文/字母数字，重跑仍由manifest复用ID。
            assert self.sql(f"SELECT COUNT(*) FROM crm_customer WHERE customer_name='{name}'") == "0"
            request = {"profile": {"customerName": name, "sourceCode": "DEBUG_SEED",
                "industryCode": industry, "region": "上海·华东演示区", "companySize": "10～50人",
                "remark": BATCH + "；合成演示客户，无真实客户或商业承诺。",
                "customerLevel": level, "tags": ["POTENTIAL", "BUDGET_CONFIRMED"]},
                "primaryContact": {"contactName": "演示联系人" + str(index),
                    "jobTitle": "模拟业务联系人", "phone": f"000103000{index:02d}",
                    "email": f"xingcha-{index}@crm-demo.example", "decisionRole": "CONTACT", "primary": True},
                "ownerId": None if target == "PUBLIC" else self.users["east.sales"]["id"],
                "sameNameConfirmed": False}
            created = self.api("POST", "/api/customers", body=request)
            entry["customerId"] = created if isinstance(created, int) else created["id"]
            self.save()
        return entry

    def follow(self, entry: dict, key: str):
        """在服务器当前阶段提交有效跟进，再由华东经理核验，不借用其他阶段记录。"""
        follows = entry.setdefault("followIds", {})
        oid, cid = entry["opportunityId"], entry["customerId"]
        if key not in follows:
            detail = self.api("GET", f"/api/opportunities/{oid}", "east.sales")
            follows[key] = self.api("POST", f"/api/opportunities/{oid}/follow-records", "east.sales", {
                "customerVersion": detail["customerVersion"], "contactId": detail["contacts"][0]["id"],
                "followMethod": "PHONE", "content": f"{BATCH}；合成演示：{key}阶段已沟通需求和下一步安排。",
                "resultCode": "SOLUTION_DISCUSSED", "substantiveResult": "演示：本阶段方案与业务要求已沟通。",
                "nextAction": "演示：继续处理下一阶段要求；非真实业务安排。",
                "nextActionAt": (now() + timedelta(days=2)).isoformat(timespec="seconds"),
                "followedAt": now().isoformat(timespec="seconds")})
            self.save()
        record = self.api("GET", f"/api/opportunities/{oid}", "east.sales")
        current = next(f for f in record["followRecords"] if f["id"] == follows[key])
        if current["reviewStatus"] == "PENDING":
            customer = self.api("GET", f"/api/customers/{cid}", "east.sales")
            self.api("PATCH", f"/api/customers/{cid}/follow-records/{follows[key]}/review", "east.manager", {
                "version": customer["version"], "reviewStatus": "CONFIRMED",
                "reviewReason": BATCH + "；合成演示跟进核验，不代表真实沟通或审批。"})
        else:
            assert current["reviewStatus"] == "CONFIRMED"

    def initial_customer_follow(self, entry: dict):
        """新私海客户先完成首次有效沟通，再按现有规则创建商机。"""
        cid = entry["customerId"]
        detail = self.api("GET", f"/api/customers/{cid}", "east.sales")
        if "initialCustomerFollowId" not in entry:
            entry["initialCustomerFollowId"] = self.api("POST", f"/api/customers/{cid}/follow-records", "east.sales", {
                "customerVersion": detail["version"], "contactId": detail["contacts"][0]["id"],
                "followMethod": "PHONE", "content": BATCH + "；演示：首次联系后确认有明确需求，准备建立商机。",
                "resultCode": "NEED_CONFIRMED", "substantiveResult": "演示：初始需求及联系人已确认。",
                "nextAction": "演示：建立商机并沟通方案。",
                "nextActionAt": (now() + timedelta(days=2)).isoformat(timespec="seconds"),
                "followedAt": now().isoformat(timespec="seconds")})
            self.save()
        detail = self.api("GET", f"/api/customers/{cid}", "east.sales")
        current = next(f for f in detail["followRecords"] if f["id"] == entry["initialCustomerFollowId"])
        if current["reviewStatus"] == "PENDING":
            self.api("PATCH", f"/api/customers/{cid}/follow-records/{current['id']}/review", "east.manager", {
                "version": detail["version"], "reviewStatus": "CONFIRMED",
                "reviewReason": BATCH + "；合成演示首次联系核验。"})

    def evidence(self, entry: dict, kind: str, amount: int):
        """登记模拟报价/合作确认，由系统按已核验跟进门槛自动推进阶段。"""
        oid = entry["opportunityId"]
        detail = self.api("GET", f"/api/opportunities/{oid}", "east.sales")
        if (kind == "QUOTE" and detail["quoteAt"]) or (kind == "CONFIRMATION" and detail["customerConfirmAt"]):
            return
        self.api("POST", f"/api/opportunities/{oid}/evidence", "east.sales", {
            "version": detail["version"], "kind": kind, "quoteAmount": amount if kind == "QUOTE" else None,
            "description": BATCH + ("；演示：已说明模拟报价金额和范围。" if kind == "QUOTE" else "；演示：模拟客户明确合作，无真实交易承诺。")})

    def contract(self, entry: dict, amount: int):
        """将登记、草稿审核、实际文件提交至FILE_REVIEW，保留最后核验供用户演示。"""
        if "contractId" not in entry:
            entry["contractId"] = self.api("POST", "/api/contracts/from-opportunity", "admin",
                                          {"opportunityId": entry["opportunityId"]})
            self.save()
        cid = entry["contractId"]
        detail = self.api("GET", f"/api/contracts/{cid}", "east.sales")
        if detail["signingProgress"] == "DRAFT_GENERATED":
            today = now().date()
            self.api("PUT", f"/api/contracts/{cid}", "east.sales", {
                "version": detail["version"], "contractName": entry["name"] + "·合同登记",
                "externalContractNo": "XC-DEMO-20261030-" + str(cid), "businessType": "演示软件服务",
                "ourPartyName": "星槎演示供应方（虚构）", "counterpartyName": entry["name"] + "（虚构）",
                "ourContactName": "演示我方经办人", "ourContactPhone": "00010300901",
                "counterpartyContactName": "演示客户经办人", "counterpartyContactPhone": "00010300902",
                "ourAddress": "演示地址，不对应真实营业场所", "counterpartyAddress": "演示客户地址",
                "contractAmount": amount, "signedAt": str(today), "plannedSignedAt": str(today),
                "performanceStartDate": str(today), "expireDate": str(today + timedelta(days=365)),
                "paymentTerms": "合成演示：验收后按登记金额付款；未登记实际收款。",
                "deliveryTerms": "合成演示：完成配置、培训及验收；不对应真实履约。",
                "description": BATCH + "；模拟合同登记，用于最后签约核验测试，所有主体及条款均为虚构。"})
            detail = self.api("GET", f"/api/contracts/{cid}", "east.sales")
            self.api("POST", f"/api/contracts/{cid}/draft-submit", "east.sales", {"version": detail["version"]})
            detail = self.api("GET", f"/api/contracts/{cid}", "east.manager")
        if detail["signingProgress"] == "DRAFT_REVIEW":
            self.api("POST", f"/api/contracts/{cid}/review", "east.manager", {
                "version": detail["version"], "pass": True, "opinion": BATCH + "；模拟登记核对通过，等待实际文件最终核验。"})
            detail = self.api("GET", f"/api/contracts/{cid}", "east.sales")
        if detail["signingProgress"] == "SIGNED_UPLOAD":
            if not any(a["materialType"] == "SIGNED_CONTRACT" for a in detail["attachments"]):
                self.upload(cid)
            detail = self.api("GET", f"/api/contracts/{cid}", "east.sales")
            self.api("POST", f"/api/contracts/{cid}/submit", "east.sales", {"version": detail["version"]})
        detail = self.api("GET", f"/api/contracts/{cid}", "east.manager")
        assert detail["signingProgress"] == "FILE_REVIEW" and detail["contractStatus"] == "PENDING_REVIEW"
        assert detail["canReview"]

    def upload(self, contract_id: int):
        """复用带有醒目演示标识的现有PDF，通过正式上传接口保存独立附件版本。"""
        source = ROOT / "output/contract-files/demo-east-20261022/demo-only.pdf"
        assert source.is_file() and source.read_bytes().startswith(b"%PDF-")
        boundary = "CrmSeed" + uuid.uuid4().hex
        body = bytearray()
        for key, value in [("materialType", "SIGNED_CONTRACT"), ("note", BATCH + "；合成文件，无真实签名签章，仅演示核验操作。")]:
            body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        body.extend((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                     f'filename="xingcha-demo-{contract_id}.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode())
        body.extend(source.read_bytes())
        body.extend(f"\r\n--{boundary}--\r\n".encode())
        self.api("POST", f"/api/contracts/{contract_id}/attachments", "east.sales", raw=bytes(body),
                 content_type="multipart/form-data; boundary=" + boundary)

    def data(self):
        """顺序建立六客户、四商机和两待核验合同，完成场景后重跑不重新推进。"""
        for index, (key, label, target, industry, level, amount) in enumerate(SCENARIOS, 1):
            entry = self.customer(key, label, target, industry, level, index)
            if entry.get("complete"):
                continue
            if target != "PUBLIC":
                if "opportunityId" not in entry:
                    self.initial_customer_follow(entry)
                    entry["opportunityId"] = self.api("POST", "/api/opportunities", body={
                        "customerId": entry["customerId"], "opportunityName": entry["name"] + "·商机",
                        "ownerId": self.users["east.sales"]["id"], "expectedAmount": amount,
                        "expectedCloseDate": str(now().date() + timedelta(days=45))})
                    self.save()
                self.follow(entry, "NEW")
                self.follow(entry, "FOLLOWING")
                self.evidence(entry, "QUOTE", amount)
                self.follow(entry, "QUOTED")
                if target == "DEAL":
                    self.evidence(entry, "CONFIRMATION", amount)
                    self.follow(entry, "DEAL")
                    self.contract(entry, amount)
            entry["complete"] = True
            self.save()

    def verify(self):
        """核对六客户、四商机、四阶段跟进及两份待核验文件与三角色范围。"""
        result = []
        for key, _, target, _, _, _ in SCENARIOS:
            entry = self.state["scenarios"][key]
            cid = entry["customerId"]
            customer = self.api("GET", f"/api/customers/{cid}")
            assert customer["seaType"] == ("PUBLIC" if target == "PUBLIC" else "PRIVATE")
            row = {"key": key, "customerId": cid, "name": entry["name"], "seaType": customer["seaType"]}
            if target == "PUBLIC":
                assert customer.get("ownerId") is None and customer.get("teamId") is None
                assert self.sql(f"SELECT COUNT(*) FROM crm_opportunity WHERE customer_id={cid}") == "0"
            else:
                assert customer["ownerId"] == self.users["east.sales"]["id"] and customer["teamId"] == self.team
                oid = entry["opportunityId"]
                for actor in ["admin", "east.manager", "east.sales"]:
                    opportunity = self.api("GET", f"/api/opportunities/{oid}", actor)
                    assert opportunity["stageCode"] == target
                    assert opportunity["ownerId"] == self.users["east.sales"]["id"]
                row.update(opportunityId=oid, stage=target)
                current = next(s for s in opportunity["stageProgress"] if s["current"])
                assert next(r for r in current["requirements"] if r["code"] == "EFFECTIVE_FOLLOW")["met"]
                required = ("QUOTE",) if target == "QUOTED" else ("QUOTE", "CUSTOMER_CONFIRMATION")
                assert all(r["met"] for r in current["requirements"] if r["code"] in required)
                if target == "QUOTED":
                    assert opportunity["customerConfirmAt"] is None
                else:
                    contract_id = entry["contractId"]
                    detail = self.api("GET", f"/api/contracts/{contract_id}", "east.manager")
                    assert detail["signingProgress"] == "FILE_REVIEW" and detail["canReview"]
                    assert self.api("GET", f"/api/contracts/{contract_id}", "admin")["canReview"]
                    assert not self.api("GET", f"/api/contracts/{contract_id}", "east.sales")["canReview"]
                    signed = next(a for a in detail["attachments"] if a["materialType"] == "SIGNED_CONTRACT")
                    assert signed["confirmStatus"] == "PENDING"
                    downloaded = self.api("GET", signed["downloadUrl"], "east.manager", content_type="download")
                    source = (ROOT / "output/contract-files/demo-east-20261022/demo-only.pdf").read_bytes()
                    assert hashlib.sha256(downloaded).digest() == hashlib.sha256(source).digest()
                    row.update(contractId=contract_id, signingProgress=detail["signingProgress"], attachmentId=signed["id"])
            result.append(row)
        holding = self.rows("SELECT JSON_OBJECT('stored',u.current_hold_count,'actual',"
            "(SELECT COUNT(*) FROM crm_customer c WHERE c.owner_id=u.id AND c.sea_type='PRIVATE' AND c.deleted=0)) "
            "FROM sys_user u WHERE u.username='east.sales'")[0]
        assert holding["stored"] == holding["actual"]
        unchanged = all(self.snapshot(t, v["maxId"]) == v["sha256"] for t, v in self.state["baseline"].items())
        assert unchanged, "已有业务数据摘要发生变化，需要人工核对"
        members = self.api("GET", "/api/users?keyword=east.collaborator&page=1&size=10", "east.manager")
        assert members["total"] == 1 and members["list"][0]["realName"] == "华东协作人"
        self.state["verification"] = {"verifiedAt": now().isoformat(timespec="seconds"), "customers": 6,
            "publicCustomers": 2, "privateOpportunities": 4, "quoted": 2, "pendingContractReview": 2,
            "eastHolding": holding, "existingBusinessUnchanged": unchanged, "scenarios": result}
        self.save()
        print(json.dumps({"account": "east.collaborator", "credentialsFile": str(CREDS),
            "backup": self.state["backup"], "verification": self.state["verification"]}, ensure_ascii=False, indent=2))


def main():
    """默认只读核验；显式--apply才允许备份后创建本批数据。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    seed = Seed()
    if args.apply:
        seed.backup()
        seed.account()
        seed.data()
    seed.verify()


if __name__ == "__main__":
    main()
