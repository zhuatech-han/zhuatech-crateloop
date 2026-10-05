<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import {
  PackageOpen,
  Boxes,
  FileCheck2,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Search,
  Plus,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  ExternalLink,
  Clock3,
  AlertCircle,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { states, buckets, kinds, actions, payload } from "./domain.js";
import { fields, commandFields } from "./forms.js";
const lang = ref(localStorage.getItem("crateloop-language") || "zh"),
  me = ref(null),
  view = ref("movements"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  kindFilter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  selected = ref(null),
  stats = ref({ states: {} }),
  modal = ref(null),
  form = ref({}),
  directories = ref({}),
  contact = ref(false),
  ledgerPage = ref(0),
  eventPage = ref(0);
const t = (zh, en) => (lang.value === "zh" ? zh : en);
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("crateloop-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
const permissions = computed(() => me.value?.permissions || []),
  can = (p) => permissions.value.includes(p),
  staff = computed(() => !me.value?.partnerId);
const business = [
    "movements",
    "pools",
    "partners",
    "adjustments",
    "statements",
  ],
  adminTypes = [
    "users",
    "roles",
    "departments",
    "menus",
    "permissions",
    "dictionaries",
    "settings",
  ];
const labels = {
  movements: ["器具交接", "Handoffs"],
  pools: ["资产池", "Asset pools"],
  partners: ["合作伙伴", "Partners"],
  adjustments: ["数量提案", "Adjustments"],
  statements: ["伙伴对账", "Statements"],
  dashboard: ["数量统计", "Statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["器具字典", "Packaging types"],
  settings: ["系统参数", "Settings"],
};
const icons = {
  movements: PackageOpen,
  pools: Boxes,
  partners: Users,
  adjustments: FileCheck2,
  statements: FileCheck2,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
    t(...(labels[view.value] || ["CrateLoop", "CrateLoop"])),
  ),
  statusName = (s) => t(...(states[s] || [s, s])),
  bucketName = (b) =>
    b === "EXTERNAL"
      ? t("外部新增", "External addition")
      : t(...(buckets[b] || [b, b])),
  kindName = (k) => t(...(kinds[k] || [k, k]));
const actionNames = {
  SAVE: ["保存单据", "Saved record"],
  FREEZE: ["冻结对账", "Frozen statement"],
  submit: ["提交申请", "Submit"],
  cancel: ["作废单据", "Cancel record"],
  approve: ["独立批准", "Approve"],
  reject: ["驳回申请", "Reject"],
  dispatch: ["确认发运", "Dispatch"],
  receive: ["登记实际接收", "Receive"],
  resolve: ["核实短缺去向", "Resolve shortage"],
  inspect: ["独立质检", "Inspect"],
  confirm: ["确认对账", "Confirm statement"],
  dispute: ["提出对账异议", "Dispute statement"],
};
const commandName = (a) => t(...(actionNames[a] || [a, a]));
const failures = {
  OUT_OF_SCOPE: ["超出当前账号的数据范围", "Outside your data scope"],
  STAFF_ONLY: ["此操作限内部岗位", "Staff only"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired. Sign in again"],
  STALE_VERSION: [
    "记录已变化，请刷新后重试",
    "Record changed. Refresh before retrying",
  ],
  INDEPENDENT_REVIEW_REQUIRED: [
    "须由另一位授权人员完成",
    "A different authorized person must complete this action",
  ],
  INVALID_STATE: ["当前状态不允许此操作", "Unavailable in this state"],
  INSUFFICIENT_QUANTITY: ["当前可用数量不足", "Insufficient quantity"],
  QUANTITY_MISMATCH: [
    "分类数量之和须等于待处理量",
    "Classified quantities must equal the pending quantity",
  ],
  INVALID_QUANTITY: [
    "数量须为0到100万的整数；申请量须大于0",
    "Use whole numbers up to one million; requests must exceed zero",
  ],
  DISABLED_RESOURCE: [
    "资产池或伙伴已停用，不能借出",
    "Pool or partner disabled; issues unavailable",
  ],
  PENDING_HANDOFF: [
    "此伙伴还有未完成交接，请先处理",
    "Complete outstanding handoffs first",
  ],
  STALE_STATEMENT: [
    "对账冻结后有新流水，请作废并重建",
    "Ledger changed after freeze. Cancel and recreate",
  ],
  OPEN_STATEMENT: ["已有未完成对账单", "An open statement already exists"],
  IMMUTABLE_IDENTITY: [
    "编号、部门和器具类型不能更改",
    "Reference, department and packaging type are fixed",
  ],
  WEAK_PASSWORD: [
    "密码至少12位并含大小写字母和数字",
    "Use at least 12 characters with upper/lowercase letters and digits",
  ],
  LAST_ADMIN: [
    "须保留一位未绑定伙伴的启用管理员",
    "Keep an enabled administrator without a partner binding",
  ],
  CONFLICT: [
    "编号重复或记录正在被引用",
    "Duplicate reference or referenced record",
  ],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password"],
  INVALID_INPUT: [
    "请检查必填项及输入格式",
    "Check required fields and formats",
  ],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts. Try later"],
  BALANCE_INVARIANT: [
    "数量核对未通过，操作已撤回，请联系管理员",
    "Balance check failed. Operation rolled back; contact your administrator",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确", "Incorrect current password"],
};
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") {
      me.value = null;
      detail.value = null;
      rows.value = [];
      modal.value = null;
    }
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  options.value = await api("/options");
  if (can("admin") && staff.value && me.value.scope === "ALL") {
    for (const k of ["roles", "permissions", "departments"])
      directories.value[k] = await api("/admin/" + k);
  }
}
async function load() {
  detail.value = null;
  selected.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (business.includes(view.value)) {
    const r = await api(
      "/" +
        view.value +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          kind: kindFilter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.items;
    total.value = r.total;
  } else {
    let all = await api(
      view.value === "audit" ? "/audit" : "/admin/" + view.value,
    );
    all = all.filter((v) =>
      Object.values(v).some(
        (x) =>
          typeof x === "string" &&
          x.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort(
      sort.value === "reference"
        ? (a, b) =>
            String(a.name || a.username || a.code).localeCompare(
              String(b.name || b.username || b.code),
            )
        : (a, b) => b.id - a.id,
    );
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  rows.value = [];
  total.value = 0;
  detail.value = null;
  stats.value = { states: {} };
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  kindFilter.value = "";
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    me.value = null;
    detail.value = null;
    directories.value = {};
    options.value = {};
    rows.value = [];
    resetCsrf();
  });
}
async function open(row) {
  await run(async () => {
    selected.value = row.id;
    detail.value = await api("/" + view.value + "/" + row.id);
    ledgerPage.value = 0;
    eventPage.value = 0;
    window.scrollTo(0, 0);
  });
}
const record = computed(() => detail.value?.record || detail.value?.pool),
  currentActions = computed(() => actions(view.value, record.value, me.value));
const snapshot = computed(() => {
  try {
    return JSON.parse(detail.value?.record?.snapshot || "{}");
  } catch {
    return {};
  }
});
const ledger = computed(() =>
    view.value === "statements"
      ? snapshot.value.ledger || []
      : detail.value?.ledger || [],
  ),
  ledgerRows = computed(() =>
    ledger.value.slice(ledgerPage.value * 20, ledgerPage.value * 20 + 20),
  ),
  events = computed(() => [...(detail.value?.events || [])].reverse()),
  eventRows = computed(() =>
    events.value.slice(eventPage.value * 12, eventPage.value * 12 + 12),
  );
const modalFields = computed(() =>
  modal.value?.kind === "command"
    ? commandFields(modal.value.action, record.value)
    : modal.value?.kind === "password"
      ? [
          ["oldPassword", "原密码", "Current password", "password"],
          ["newPassword", "新密码", "New password", "password"],
        ]
      : (fields[modal.value?.type] || []).filter(
          (f) =>
            !(
              modal.value?.type === "adjustments" &&
              form.value.kind !== "REPAIR" &&
              ["goodQuantity", "retireQuantity"].includes(f[0])
            ),
        ),
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((v) => ({
      value: v,
      label: {
        ALL: t("全部", "All"),
        DEPARTMENT: t("本部门", "Department"),
        SELF: t("本人创建", "Created by self"),
      }[v],
    }));
  if (["movementKinds", "adjustmentKinds"].includes(key))
    return (
      key === "movementKinds"
        ? ["ISSUE", "RETURN"]
        : ["ADD", "REPAIR", "RETIRE"]
    ).map((v) => ({ value: v, label: kindName(v) }));
  let list = directories.value[key] || options.value[key] || [];
  if (
    key === "partners" &&
    ["movements", "statements"].includes(modal.value?.type) &&
    form.value.poolId
  ) {
    const pool = options.value.pools?.find(
      (p) => p.id === Number(form.value.poolId),
    );
    list = list.filter((p) => p.departmentId === pool?.departmentId);
  }
  return list.map((v) => ({
    value: ["permissions", "packagingTypes"].includes(key) ? v.code : v.id,
    label:
      lang.value === "en" && v.nameEn
        ? v.nameEn
        : (v.reference ? v.reference + " · " : "") +
          (v.name || v.displayName || v.code),
  }));
}
function edit(type, row = null) {
  error.value = "";
  modal.value = { kind: "edit", type, id: row?.id };
  form.value = row
    ? { ...row }
    : {
        enabled: true,
        departmentId: me.value.departmentId,
        scope: "DEPARTMENT",
        permissions: [],
        type: "packaging",
        kind:
          type === "pools" ? "CRATE" : type === "movements" ? "ISSUE" : "ADD",
        quantity: 1,
        goodQuantity: 0,
        retireQuantity: 0,
        partnerId: me.value.partnerId || "",
      };
  if (type === "users") form.value.password = "";
}
function command(action) {
  error.value = "";
  modal.value = {
    kind: "command",
    type: view.value,
    action,
    id: selected.value,
  };
  form.value = {
    note: "",
    quantity: record.value.quantity,
    recoveredQuantity: 0,
    lostQuantity: 0,
    backToPartner: 0,
    goodQuantity: record.value.receiptQuantity || 0,
    repairQuantity: 0,
    retireQuantity: 0,
  };
}
function remove(type, row) {
  error.value = "";
  modal.value = { kind: "delete", type, id: row.id };
  form.value = {};
}
async function save() {
  await run(async () => {
    const m = modal.value;
    let body = payload(form.value, modalFields.value);
    if (m.kind === "password") {
      await api("/auth/password", "POST", body);
      me.value = null;
      modal.value = null;
      resetCsrf();
      return;
    }
    if (m.kind === "delete") {
      await api(
        "/" +
          (adminTypes.includes(m.type) ? "admin/" : "") +
          m.type +
          "/" +
          m.id,
        "DELETE",
        {},
      );
    } else {
      if (business.includes(m.type)) {
        body.version =
          m.kind === "command"
            ? record.value.version
            : (form.value.version ?? null);
        const signature = JSON.stringify(body);
        if (signature !== m.signature) {
          m.requestKey = crypto.randomUUID();
          m.signature = signature;
        }
        body.requestKey = m.requestKey;
      }
      const url =
        "/" +
        (adminTypes.includes(m.type) ? "admin/" : "") +
        m.type +
        (m.id ? "/" + m.id : "") +
        (m.kind === "command" ? "/commands/" + m.action : "");
      const result = await api(
        url,
        m.kind === "edit" && m.id ? "PUT" : "POST",
        body,
      );
      if (m.kind === "command") detail.value = result;
    }
    modal.value = null;
    notice.value = t("已保存", "Saved");
    await loadOptions();
    if (m.kind !== "command") await load();
  });
}
function formatTime(value) {
  return value
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: options.value.timezone || "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(value))
    : "—";
}
function fieldValue(row, key) {
  if (view.value === "settings" && key === "code")
    return t(
      ...({
        companyName: ["显示名称", "Display name"],
        timezone: ["业务时区", "Business timezone"],
        maxMovements: ["每类资源上限", "Resource limit per type"],
      }[row.code] || [row.code, row.code]),
    );
  if (key === "createdAt") return formatTime(row[key]);
  if (key === "status") return statusName(row[key]);
  if (key === "kind")
    return kinds[row[key]]
      ? kindName(row[key])
      : choices("packagingTypes").find((c) => c.value === row[key])?.label ||
          row[key];
  if (key === "scope")
    return (
      choices("scope").find((c) => c.value === row[key])?.label || row[key]
    );
  if (key === "enabled")
    return row[key] ? t("启用", "Enabled") : t("停用", "Disabled");
  if (key === "permissions")
    return (row.permissions?.length || 0) + t(" 项权限", " permissions");
  if (["roleId", "departmentId", "poolId", "partnerId"].includes(key)) {
    const list =
      key === "roleId"
        ? directories.value.roles
        : key === "departmentId"
          ? directories.value.departments || options.value.departments
          : key === "poolId"
            ? options.value.pools
            : options.value.partners;
    return list?.find((r) => r.id === row[key])?.name || row[key] || "—";
  }
  return row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      movements: [
        ["reference", "交接单号", "Reference"],
        ["kind", "方向", "Direction"],
        ["poolId", "资产池", "Pool"],
        ["partnerId", "伙伴", "Partner"],
        ["quantity", "数量", "Quantity"],
        ["status", "状态", "Status"],
      ],
      pools: [
        ["reference", "资产池编号", "Reference"],
        ["name", "名称", "Name"],
        ["kind", "器具", "Type"],
        ["total", "资产总量", "Total"],
        ["available", "可用", "Available"],
        ["enabled", "状态", "Status"],
      ],
      adjustments: [
        ["reference", "提案编号", "Reference"],
        ["kind", "类型", "Type"],
        ["poolId", "资产池", "Pool"],
        ["quantity", "数量", "Quantity"],
        ["status", "状态", "Status"],
      ],
      statements: [
        ["reference", "对账单号", "Reference"],
        ["poolId", "资产池", "Pool"],
        ["partnerId", "伙伴", "Partner"],
        ["held", "冻结保管量", "Frozen custody"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "操作时间", "Time"],
        ["actor", "操作人", "Actor"],
        ["action", "操作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const canCreate = computed(() =>
  business.includes(view.value)
    ? view.value === "movements"
      ? can("movement.write")
      : staff.value &&
        can(
          view.value === "adjustments"
            ? "adjustment.write"
            : view.value === "statements"
              ? "statement.write"
              : "catalog.write",
        )
    : staff.value &&
      can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
const canEdit = computed(
  () =>
    staff.value &&
    ((can("admin") && adminTypes.includes(view.value)) ||
      (["pools", "partners"].includes(view.value) && can("catalog.write"))),
);
const canDraftEdit = computed(
  () =>
    record.value?.status === "DRAFT" &&
    can(view.value === "movements" ? "movement.write" : "adjustment.write") &&
    ["movements", "adjustments"].includes(view.value),
);
const filters = computed(() =>
  view.value === "movements"
    ? [
        "DRAFT",
        "SUBMITTED",
        "RESERVED",
        "DISPATCHED",
        "DISPUTED",
        "QC_PENDING",
        "CLOSED",
        "REJECTED",
        "CANCELLED",
      ]
    : view.value === "adjustments"
      ? ["DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED"]
      : view.value === "statements"
        ? ["OPEN", "DISPUTED", "CONFIRMED", "CANCELLED"]
        : [],
);
function escape(e) {
  if (e.key === "Escape" && !busy.value) {
    modal.value = null;
    contact.value = false;
  }
}
onMounted(async () => {
  document.addEventListener("keydown", escape);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  await run(async () => {
    try {
      me.value = await api("/auth/me");
    } catch (e) {
      if (e.message === "UNAUTHENTICATED") return;
      throw e;
    }
    await loadOptions();
    view.value = me.value.menus[0]?.code || "dashboard";
    await load();
  });
});
onUnmounted(() => document.removeEventListener("keydown", escape));
</script>
<template>
  <div v-if="!me" class="login-shell">
    <section class="login-story">
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技 ZhuaTech" class="brand-logo"
      /></a>
      <div class="story-copy">
        <p class="eyebrow">CRATELOOP / RETURNABLE PACKAGING</p>
        <h1>
          {{ t("周转器具", "Returnable packaging") }}<br />{{
            t("往来台账", "custody ledger")
          }}
        </h1>
        <p>
          {{
            t(
              "借出 · 交接 · 归还 · 对账",
              "Issue · Handoff · Return · Reconcile",
            )
          }}
        </p>
        <div class="crate-art" aria-hidden="true">
          <div class="crate"><span>CRATE / 01</span></div>
          <div class="crate small"><span>CRATE / 02</span></div>
          <div class="pallet"></div>
        </div>
      </div>
      <p class="story-footer">
        {{ t("周转箱与托盘数量管理", "Crate and pallet quantity management") }}
      </p>
    </section>
    <section class="login-panel">
      <button class="language" @click="language">
        {{ lang === "zh" ? "English" : "中文" }}
      </button>
      <form class="login-form" @submit.prevent="signIn">
        <span class="small-mark">CRATELOOP</span>
        <h2>{{ t("登录工作台", "Sign in") }}</h2>
        <p class="muted">
          {{
            t("使用已开通的岗位账号继续", "Continue with your assigned account")
          }}
        </p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="128"
        /></label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary wide" :disabled="busy">
          {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in")
          }}<ArrowRight :size="18" />
        </button>
        <p class="license">
          {{
            t(
              "0.1.0 · 公开源码学习版 · 非商业授权",
              "0.1.0 · Source learning edition · Noncommercial",
            )
          }}
        </p>
      </form>
      <footer class="login-contact">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技</a
        ><button class="text-button" @click="contact = true">
          {{ t("商业咨询", "Commercial enquiries") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noopener"
        ><img src="/brand/logo.jpg" alt="知华科技 ZhuaTech"
      /></a>
      <div class="product">
        <PackageOpen :size="22" /><strong>CrateLoop</strong><span>0.1</span>
      </div>
      <nav aria-label="Navigation">
        <button
          v-for="m in me.menus"
          :key="m.code"
          :class="{ active: view === m.code }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <span>{{ t("公开源码学习版", "Source learning edition") }}</span
        ><button @click="contact = true">
          {{ t("知华商业咨询", "ZhuaTech enquiries")
          }}<ExternalLink :size="13" />
        </button>
      </div>
    </aside>
    <main>
      <header class="topbar">
        <div>
          <span class="eyebrow">{{ options.companyName }}</span>
          <h1>{{ title }}</h1>
        </div>
        <div class="account">
          <button class="language" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><button
            class="profile"
            @click="
              modal = { kind: 'password' };
              form = {};
              error = '';
            "
          >
            <span class="avatar">{{ me.displayName.slice(0, 1) }}</span
            ><span
              >{{ me.displayName }}<small>{{ me.role }}</small></span
            ></button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            @click="signOut"
          >
            <LogOut :size="19" />
          </button>
        </div>
      </header>
      <section class="workspace">
        <p v-if="error && !modal" class="error" role="alert">{{ error }}</p>
        <p v-if="notice" class="notice" role="status">{{ notice }}</p>
        <template v-if="view === 'dashboard'"
          ><div class="section-heading">
            <div>
              <h2>{{ t("当前数量概览", "Current quantity overview") }}</h2>
              <p class="muted">
                {{ t("当前账号可见范围", "Your visible data scope") }}
              </p>
            </div>
            <button :disabled="busy" @click="run(load)">
              <RefreshCw :size="16" />{{ t("刷新", "Refresh") }}
            </button>
          </div>
          <div class="metric-grid">
            <article>
              <span>{{ t("交接单据", "Handoffs") }}</span
              ><strong>{{ stats.movements || 0 }}</strong>
            </article>
            <article>
              <span>{{ t("待处理差异", "Unresolved shortages") }}</span
              ><strong>{{ stats.disputed || 0 }}</strong>
            </article>
            <article>
              <span>{{
                staff
                  ? t("资产总量", "Total assets")
                  : t("伙伴保管量", "Partner custody")
              }}</span
              ><strong>{{ staff ? stats.total || 0 : stats.held || 0 }}</strong>
            </article>
            <article class="accent">
              <span>{{
                staff
                  ? t("当前可用", "Available")
                  : t("归还预留", "Return reserved")
              }}</span
              ><strong>{{
                staff ? stats.totals?.available || 0 : stats.returnReserved || 0
              }}</strong>
            </article>
          </div>
          <div class="detail-grid">
            <section class="panel">
              <h3>{{ t("交接进度", "Handoff progress") }}</h3>
              <div v-for="(n, s) in stats.states" :key="s" class="stat-row">
                <span>{{ statusName(s) }}</span>
                <div class="bar-track">
                  <div
                    :style="{ width: (n / (stats.movements || 1)) * 100 + '%' }"
                  ></div>
                </div>
                <strong>{{ n }}</strong>
              </div>
              <p v-if="!stats.movements" class="empty">
                {{ t("还没有交接记录", "No handoffs yet") }}
              </p>
            </section>
            <section class="panel">
              <h3>
                {{
                  staff
                    ? t("资产状态分布", "Asset buckets")
                    : t("伙伴分池余额", "Custody by pool")
                }}
              </h3>
              <div v-if="staff" class="bucket-list">
                <div v-for="(n, b) in stats.totals" :key="b">
                  <span>{{ bucketName(b) }}</span
                  ><strong>{{ n }}</strong>
                </div>
              </div>
              <div v-else>
                <div
                  v-for="b in stats.balances"
                  :key="b.poolId"
                  class="balance-row"
                >
                  <span>{{
                    options.pools?.find((p) => p.id === b.poolId)?.name ||
                    b.poolId
                  }}</span
                  ><strong>{{ b.held }} / {{ b.returnReserved }}</strong>
                </div>
                <p class="muted">
                  {{ t("保管量 / 归还预留量", "Custody / Return reserved") }}
                </p>
              </div>
            </section>
          </div></template
        >
        <template v-else-if="detail"
          ><button class="back text-button" :disabled="busy" @click="run(load)">
            <ChevronLeft :size="18" />{{ t("返回列表", "Back to list") }}
          </button>
          <section class="case-heading">
            <div>
              <span class="eyebrow">{{ record.reference }}</span>
              <h2>
                {{
                  view === "pools"
                    ? record.name
                    : view === "statements"
                      ? t("伙伴余额对账", "Partner custody statement")
                      : kindName(record.kind)
                }}
              </h2>
              <p class="muted">
                {{ detail.pool?.name
                }}<template v-if="detail.partner">
                  · {{ detail.partner.name }}</template
                >
              </p>
            </div>
            <span v-if="record.status" :class="['badge', record.status]">{{
              statusName(record.status)
            }}</span>
          </section>
          <div class="actionbar">
            <button
              v-for="a in currentActions"
              :key="a"
              :class="{ primary: !['cancel', 'dispute', 'reject'].includes(a) }"
              :disabled="busy"
              @click="command(a)"
            >
              {{ commandName(a) }}</button
            ><button
              v-if="canDraftEdit"
              :disabled="busy"
              @click="edit(view, record)"
            >
              {{ t("编辑草稿", "Edit draft") }}</button
            ><button
              v-if="view === 'pools' && canEdit"
              :disabled="busy"
              @click="edit('pools', record)"
            >
              {{ t("编辑资产池", "Edit pool") }}</button
            ><a
              v-if="can('export')"
              class="button"
              :href="'/api/' + view + '/' + selected + '/report.json'"
              download
              ><Download :size="16" />{{ t("导出证据", "Export evidence") }}</a
            >
          </div>
          <section v-if="view === 'pools'" class="panel">
            <div class="section-heading">
              <h3>{{ t("池数量", "Pool quantities") }}</h3>
              <strong>{{ t("总量", "Total") }} {{ record.total }}</strong>
            </div>
            <div class="bucket-list">
              <div v-for="(label, b) in buckets" :key="b">
                <span>{{ t(...label) }}</span
                ><strong>{{ record[b] }}</strong>
              </div>
            </div>
            <h3>{{ t("伙伴子账", "Partner subledger") }}</h3>
            <div
              v-for="b in detail.balances"
              :key="b.partnerId"
              class="balance-row"
            >
              <span>{{ fieldValue(b, "partnerId") }}</span
              ><strong
                >{{ t("保管", "Custody") }} {{ b.held }} ·
                {{ t("归还预留", "Return reserved") }}
                {{ b.returnReserved }}</strong
              >
            </div>
            <p v-if="!detail.balances.length" class="empty">
              {{ t("还没有伙伴余额", "No partner balances yet") }}
            </p>
          </section>
          <div v-else class="detail-grid">
            <section class="panel">
              <h3>{{ t("单据数量", "Document quantities") }}</h3>
              <dl class="facts">
                <template v-if="view === 'movements'"
                  ><div>
                    <dt>{{ t("申请数量", "Requested") }}</dt>
                    <dd>{{ record.quantity }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("已签收 / 接收", "Received") }}</dt>
                    <dd>{{ record.receiptQuantity }}</dd>
                  </div>
                  <div>
                    <dt>
                      {{ t("补签收 / 留在伙伴", "Recovered / With partner") }}
                    </dt>
                    <dd>
                      {{
                        record.kind === "ISSUE"
                          ? record.recoveredQuantity
                          : record.backToPartner
                      }}
                    </dd>
                  </div>
                  <div>
                    <dt>{{ t("确认遗失", "Confirmed lost") }}</dt>
                    <dd>{{ record.lostQuantity }}</dd>
                  </div>
                  <div v-if="record.kind === 'RETURN'">
                    <dt>
                      {{ t("可用 / 维修 / 报废", "Good / Repair / Retired") }}
                    </dt>
                    <dd>
                      {{ record.goodQuantity }} / {{ record.repairQuantity }} /
                      {{ record.retireQuantity }}
                    </dd>
                  </div></template
                ><template v-if="view === 'adjustments'"
                  ><div>
                    <dt>{{ t("提案数量", "Proposed quantity") }}</dt>
                    <dd>{{ record.quantity }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("可用 / 报废", "Released / Retired") }}</dt>
                    <dd>
                      {{ record.goodQuantity }} / {{ record.retireQuantity }}
                    </dd>
                  </div></template
                ><template v-if="view === 'statements'"
                  ><div>
                    <dt>{{ t("冻结伙伴保管量", "Frozen custody") }}</dt>
                    <dd>{{ record.held }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("流水截止编号", "Ledger cutoff") }}</dt>
                    <dd>{{ record.cutoff }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("冻结时间", "Frozen at") }}</dt>
                    <dd>{{ formatTime(record.createdAt) }}</dd>
                  </div></template
                >
              </dl>
            </section>
            <section class="panel">
              <h3>
                {{
                  detail.balance
                    ? t("伙伴当前余额", "Current partner balance")
                    : t("器具档案", "Packaging record")
                }}
              </h3>
              <dl class="facts">
                <div>
                  <dt>{{ t("资产池", "Pool") }}</dt>
                  <dd>{{ detail.pool.reference }} · {{ detail.pool.name }}</dd>
                </div>
                <template v-if="detail.balance"
                  ><div>
                    <dt>{{ t("保管量", "Custody") }}</dt>
                    <dd>{{ detail.balance.held }}</dd>
                  </div>
                  <div>
                    <dt>{{ t("归还预留量", "Return reserved") }}</dt>
                    <dd>{{ detail.balance.returnReserved }}</dd>
                  </div></template
                >
                <div>
                  <dt>{{ t("记录版本", "Record version") }}</dt>
                  <dd>{{ record.version }}</dd>
                </div>
              </dl>
            </section>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h3>
                {{
                  view === "statements"
                    ? t("冻结往来流水", "Frozen partner ledger")
                    : t("数量流水", "Quantity ledger")
                }}
              </h3>
              <span class="muted"
                >{{ ledger.length }} {{ t("条", "entries") }}</span
              >
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("来源单号", "Reference") }}</th>
                    <th>{{ t("转出", "From") }}</th>
                    <th>{{ t("转入", "To") }}</th>
                    <th>{{ t("数量", "Quantity") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="e in ledgerRows" :key="e.id">
                    <td>{{ formatTime(e.createdAt) }}</td>
                    <td>{{ e.reference }}</td>
                    <td>{{ bucketName(e.fromBucket) }}</td>
                    <td>{{ bucketName(e.toBucket) }}</td>
                    <td class="number">{{ e.quantity }}</td>
                  </tr>
                  <tr v-if="!ledger.length">
                    <td colspan="5" class="empty">
                      {{ t("尚无数量过账", "No quantity postings yet") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="ledger.length > 20" class="pagination">
              <button :disabled="ledgerPage === 0" @click="ledgerPage--">
                <ChevronLeft :size="16" /></button
              ><span
                >{{ ledgerPage + 1 }} /
                {{ Math.ceil(ledger.length / 20) }}</span
              ><button
                :disabled="(ledgerPage + 1) * 20 >= ledger.length"
                @click="ledgerPage++"
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </section>
          <section v-if="events.length" class="panel">
            <h3>{{ t("操作凭据", "Action evidence") }}</h3>
            <div v-for="e in eventRows" :key="e.id" class="evidence">
              <div>
                <strong>{{ commandName(e.action) }}</strong
                ><small
                  >{{ formatTime(e.createdAt) }} ·
                  {{ t("操作人", "Actor") }} #{{ e.actorId }}</small
                >
              </div>
              <p>{{ e.note || t("保存单据", "Saved record") }}</p>
            </div>
            <div v-if="events.length > 12" class="pagination">
              <button :disabled="eventPage === 0" @click="eventPage--">
                <ChevronLeft :size="16" /></button
              ><span
                >{{ eventPage + 1 }} / {{ Math.ceil(events.length / 12) }}</span
              ><button
                :disabled="(eventPage + 1) * 12 >= events.length"
                @click="eventPage++"
              >
                <ChevronRight :size="16" />
              </button>
            </div></section
        ></template>
        <template v-else
          ><div class="toolbar">
            <form
              class="search"
              @submit.prevent="
                page = 0;
                run(load);
              "
            >
              <Search :size="17" /><input
                v-model="search"
                :placeholder="t('搜索编号或名称', 'Search reference or name')"
                :aria-label="t('搜索', 'Search')"
                maxlength="200"
              /><button :disabled="busy">{{ t("查询", "Search") }}</button>
            </form>
            <select
              v-if="filters.length"
              v-model="filter"
              :aria-label="t('状态筛选', 'Status filter')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option v-for="s in filters" :key="s" :value="s">
                {{ statusName(s) }}
              </option></select
            ><select
              v-if="['movements', 'adjustments'].includes(view)"
              v-model="kindFilter"
              :aria-label="t('类型筛选', 'Type filter')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="">{{ t("全部类型", "All types") }}</option>
              <option
                v-for="k in view === 'movements'
                  ? ['ISSUE', 'RETURN']
                  : ['ADD', 'REPAIR', 'RETIRE']"
                :key="k"
                :value="k"
              >
                {{ kindName(k) }}
              </option></select
            ><select
              v-model="sort"
              :aria-label="t('排序', 'Sort')"
              @change="
                page = 0;
                run(load);
              "
            >
              <option value="newest">{{ t("最新创建", "Newest") }}</option>
              <option value="reference">
                {{ t("编号 / 名称", "Reference / Name") }}
              </option></select
            ><button
              v-if="canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="16" />{{
                view === "statements"
                  ? t("冻结对账", "Freeze statement")
                  : t("新建", "New")
              }}</button
            ><button
              class="icon-button"
              :aria-label="t('刷新', 'Refresh')"
              :disabled="busy"
              @click="run(load)"
            >
              <RefreshCw :size="18" />
            </button>
          </div>
          <section class="panel list-panel">
            <div class="list-heading">
              <span>{{ title }}</span
              ><small>{{ total }} {{ t("条记录", "records") }}</small>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-for="c in columns" :key="c[0]">
                      {{ t(c[1], c[2]) }}
                    </th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in rows" :key="row.id">
                    <td v-for="c in columns" :key="c[0]">
                      <span
                        v-if="c[0] === 'status'"
                        :class="['badge', row.status]"
                        >{{ statusName(row.status) }}</span
                      ><span v-else>{{ fieldValue(row, c[0]) }}</span>
                    </td>
                    <td class="row-actions">
                      <button
                        v-if="business.includes(view) && view !== 'partners'"
                        class="text-button"
                        :disabled="busy"
                        @click="open(row)"
                      >
                        {{ t("详情", "Details")
                        }}<ArrowRight :size="14" /></button
                      ><template v-if="canEdit"
                        ><button
                          class="text-button"
                          :disabled="busy"
                          @click="edit(view, row)"
                        >
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          v-if="
                            !['permissions', 'menus', 'settings'].includes(view)
                          "
                          class="text-button danger"
                          :disabled="busy"
                          @click="remove(view, row)"
                        >
                          {{ t("删除", "Delete") }}
                        </button></template
                      >
                    </td>
                  </tr>
                  <tr v-if="!rows.length">
                    <td :colspan="columns.length + 1" class="empty">
                      <PackageOpen :size="28" />
                      <p>
                        {{
                          search || filter || kindFilter
                            ? t("没有匹配记录", "No matching records")
                            : t("还没有记录", "No records yet")
                        }}
                      </p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="pagination">
              <span>{{
                total
                  ? t("第 ", "Page ") +
                    (page + 1) +
                    " / " +
                    Math.ceil(total / 12)
                  : t("0 条记录", "0 records")
              }}</span
              ><button
                :aria-label="t('上一页', 'Previous page')"
                :disabled="page === 0 || busy"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="17" /></button
              ><button
                :aria-label="t('下一页', 'Next page')"
                :disabled="(page + 1) * 12 >= total || busy"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="17" />
              </button>
            </div></section
        ></template>
      </section>
      <footer class="app-footer">
        <span
          >© 2026
          {{
            t(
              "知华科技 · 非商业源码学习版",
              "ZhuaTech · Noncommercial source edition",
            )
          }}</span
        ><button class="text-button" @click="contact = true">
          {{ t("商业授权与系统集成", "Commercial licensing & integration") }}
        </button>
      </footer>
    </main>
  </div>
  <div v-if="modal" class="overlay" @click.self="!busy && (modal = null)">
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialog-title"
    >
      <header>
        <div>
          <span class="eyebrow">CRATELOOP</span>
          <h2 id="dialog-title">
            {{
              modal.kind === "command"
                ? commandName(modal.action)
                : modal.kind === "delete"
                  ? t("确认删除", "Confirm deletion")
                  : modal.kind === "password"
                    ? t("修改密码", "Change password")
                    : t(
                        modal.id ? "编辑记录" : "新建记录",
                        modal.id ? "Edit record" : "New record",
                      )
            }}
          </h2>
        </div>
        <button
          class="icon-button"
          :disabled="busy"
          :aria-label="t('关闭', 'Close')"
          @click="modal = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <p v-if="modal.kind === 'delete'" class="muted">
          {{
            t(
              "仅可删除未被业务引用的记录。",
              "Only records without references can be deleted.",
            )
          }}
        </p>
        <div v-if="modal.kind !== 'delete'" class="form-grid">
          <div
            class="form-field"
            v-for="f in modalFields"
            :key="f[0]"
            :class="{
              full: f[3] === 'permissions' || f[3] === 'textarea',
              check: f[3] === 'boolean',
            }"
          >
            <template v-if="f[3] === 'boolean'"
              ><input
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                type="checkbox"
              /><label :for="'field-' + f[0]">{{
                t(f[1], f[2])
              }}</label></template
            ><template v-else
              ><span v-if="f[3] === 'permissions'">{{ t(f[1], f[2]) }}</span
              ><label v-else :for="'field-' + f[0]">{{ t(f[1], f[2]) }}</label>
              <div v-if="f[3] === 'permissions'" class="permission-grid">
                <label v-for="p in directories.permissions" :key="p.code"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ lang === "zh" ? p.name : p.code }}</label
                >
              </div>
              <select
                v-else-if="f[3] === 'select' || f[3] === 'id'"
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                :required="f[0] !== 'partnerId' || modal.type !== 'users'"
              >
                <option value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="c in choices(f[4])"
                  :key="c.value"
                  :value="c.value"
                >
                  {{ c.label }}
                </option></select
              ><textarea
                v-else-if="f[3] === 'textarea'"
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                required
                maxlength="1000"
                rows="3"
              ></textarea
              ><input
                v-else
                :id="'field-' + f[0]"
                v-model="form[f[0]]"
                :type="
                  f[3] === 'integer'
                    ? 'number'
                    : f[3] === 'money'
                      ? 'text'
                      : f[3] || 'text'
                "
                :inputmode="f[3] === 'money' ? 'decimal' : undefined"
                :min="f[3] === 'integer' ? 0 : undefined"
                :max="f[3] === 'integer' ? 1000000 : undefined"
                :step="f[3] === 'integer' ? 1 : undefined"
                :required="
                  !['password'].includes(f[0]) ||
                  (f[0] === 'password' && !modal.id)
                "
                :maxlength="f[3] === 'password' ? 128 : 200"
                :autocomplete="f[3] === 'password' ? 'new-password' : 'off'"
            /></template>
          </div>
        </div>
        <p v-if="error" class="error" role="alert">
          <AlertCircle :size="17" />{{ error }}
        </p>
        <footer>
          <button type="button" :disabled="busy" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("正在保存…", "Saving…") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="overlay" @click.self="contact = false">
    <section
      class="dialog contact-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="contact-title"
    >
      <header>
        <h2 id="contact-title">{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img src="/brand/logo.jpg" alt="知华科技 ZhuaTech" class="brand-logo" />
      <p>上海如静知华信息科技有限公司</p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >www.zhuatech.cn</a
      >
      <p class="muted">
        {{
          t(
            "商业授权、定制开发、部署与系统集成",
            "Commercial licensing, custom development, deployment and integration",
          )
        }}
      </p>
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
          <figcaption>微信 zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
          <figcaption>微信 zhuatech2</figcaption>
        </figure>
      </div>
      <p class="license">
        {{
          t(
            "公开源码学习版。商业使用需另行取得书面授权。",
            "Source learning edition. Commercial use requires separate written authorization.",
          )
        }}
      </p>
    </section>
  </div>
</template>
