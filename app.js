
/* Nephi Work — static demo
   Data stored in localStorage: nw_user, nw_owner, nw_orders, nw_myorders
*/
const $ = (sel, root=document) => root.querySelector(sel);
const $$ = (sel, root=document) => [...root.querySelectorAll(sel)];

const STORAGE = {
  user: "nw_user",
  owner: "nw_owner",
  orders: "nw_orders",
  myOrders: "nw_myorders"
};

const OWNER_LOGIN = "Nephi";
const OWNER_PASS = "Necori0703";

const DIFF = {
  easy: { label: "Лёгкая", rank: 1 },
  medium: { label: "Средняя", rank: 2 },
  hard: { label: "Сложная", rank: 3 },
  ultra: { label: "Ультра", rank: 4 },
  impossible: { label: "Невозможная", rank: 5 },
};

const DEMO_ORDERS = [
  {
    id: "o1",
    title: "Лендинг под услугу + адаптив",
    desc: "Сверстать аккуратный лендинг (до 6 блоков), добавить плавные анимации и форму заявки.",
    difficulty: "easy",
    price: 3500,
    tags: ["сайт", "верстка", "анимации"]
  },
  {
    id: "o2",
    title: "Дизайн карточек товара (10 шт.)",
    desc: "Сделать единый стиль, подготовить под соцсети и маркетплейс. Исходники + экспорт.",
    difficulty: "easy",
    price: 2500,
    tags: ["дизайн", "карточки", "баннер"]
  },
  {
    id: "o3",
    title: "Телеграм-бот: заявки + админ-меню",
    desc: "Бот принимает заявки, пишет в канал/чат, есть команда /admin для просмотра списка заявок.",
    difficulty: "medium",
    price: 9000,
    tags: ["telegram", "бот", "python"]
  },
  {
    id: "o4",
    title: "Интеграция оплат + выдача товара",
    desc: "Подключить платёж, после оплаты показать выдачу/инструкции. Логи и защита от дублей.",
    difficulty: "hard",
    price: 18000,
    tags: ["оплата", "интеграция", "безопасность"]
  },
  {
    id: "o5",
    title: "Рефакторинг фронтенда + производительность",
    desc: "Оптимизировать загрузку, убрать лишние перерисовки, привести стили и компоненты к единому виду.",
    difficulty: "ultra",
    price: 27000,
    tags: ["frontend", "оптимизация", "архитектура"]
  },
  {
    id: "o6",
    title: "Сложная система ролей и прав (RBAC)",
    desc: "Проектирование ролей, матрица прав, UI-ограничения, аудит-лог. Документация.",
    difficulty: "impossible",
    price: 45000,
    tags: ["rbac", "backend", "аудит"]
  }
];

function uid(){
  return Math.random().toString(16).slice(2) + Date.now().toString(16);
}
function money(n){
  const x = Number(n) || 0;
  return x.toLocaleString("ru-RU") + " ₽";
}
function safeParse(json, fallback){
  try{ return JSON.parse(json); } catch { return fallback; }
}
function load(key, fallback){
  return safeParse(localStorage.getItem(key) ?? "", fallback);
}
function save(key, value){
  localStorage.setItem(key, JSON.stringify(value));
}

function toast(msg){
  const t = $("#toast");
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast._timer);
  toast._timer = setTimeout(()=>{ t.hidden = true; }, 2600);
}

function smoothScrollTo(id){
  const el = document.getElementById(id);
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block:"start" });
}

function ensureOrders(){
  const existing = load(STORAGE.orders, null);
  if (Array.isArray(existing) && existing.length) return existing;
  save(STORAGE.orders, DEMO_ORDERS);
  return DEMO_ORDERS;
}

function isOwner(){
  return load(STORAGE.owner, { isOwner:false }).isOwner === true;
}

/* THEME: pick average color from background image */
async function applyThemeFromBg(){
  const img = new Image();
  img.src = "assets/background.gif";
  await img.decode().catch(()=>null);
  if (!img.naturalWidth) return;
  const canvas = document.createElement("canvas");
  const w = 80, h = 80;
  canvas.width = w; canvas.height = h;
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  ctx.drawImage(img, 0, 0, w, h);
  const data = ctx.getImageData(0,0,w,h).data;
  let r=0,g=0,b=0,c=0;
  for(let i=0;i<data.length;i+=4){
    const a = data[i+3];
    if (a < 25) continue;
    r += data[i]; g += data[i+1]; b += data[i+2];
    c++;
  }
  if (!c) return;
  r = Math.round(r/c); g = Math.round(g/c); b = Math.round(b/c);

  // build accents: slightly brighter and slightly darker
  const brighten = (v, k)=> Math.min(255, Math.round(v + (255 - v) * k));
  const darken = (v, k)=> Math.max(0, Math.round(v * (1-k)));
  const accent = `rgb(${brighten(r,0.18)},${brighten(g,0.10)},${brighten(b,0.10)})`;
  const accent2 = `rgb(${darken(r,0.08)},${darken(g,0.06)},${darken(b,0.06)})`;
  document.documentElement.style.setProperty("--accent", accent);
  document.documentElement.style.setProperty("--accent2", accent2);
}

/* ORDERS UI */
function renderOrders(){
  const orders = load(STORAGE.orders, DEMO_ORDERS);
  const q = ($("#searchInput").value || "").trim().toLowerCase();
  const diff = $("#difficultySelect").value;
  const sort = $("#sortSelect").value;

  let items = orders.slice();

  if (q){
    items = items.filter(o => {
      const hay = [o.title, o.desc, ...(o.tags||[])].join(" ").toLowerCase();
      return hay.includes(q);
    });
  }
  if (diff !== "all"){
    items = items.filter(o => o.difficulty === diff);
  }

  if (sort === "priceDesc") items.sort((a,b)=> (b.price||0)-(a.price||0));
  if (sort === "priceAsc") items.sort((a,b)=> (a.price||0)-(b.price||0));
  if (sort === "difficulty") items.sort((a,b)=> (DIFF[a.difficulty]?.rank||9)-(DIFF[b.difficulty]?.rank||9));
  if (sort === "new") items.sort((a,b)=> (b._createdAt||0)-(a._createdAt||0));

  const grid = $("#ordersGrid");
  if (!items.length){
    grid.innerHTML = `<div class="muted">Ничего не найдено.</div>`;
    return;
  }

  grid.innerHTML = items.map(o => `
    <article class="card">
      <div class="card-top">
        <div>
          <div class="card-title">${escapeHtml(o.title)}</div>
          <div class="muted small">${escapeHtml(DIFF[o.difficulty]?.label ?? "—")}</div>
        </div>
        <div class="badges">
          <span class="badge diff-${o.difficulty}">${escapeHtml(DIFF[o.difficulty]?.label ?? o.difficulty)}</span>
          <span class="badge">${money(o.price)}</span>
        </div>
      </div>

      <div class="card-desc">${escapeHtml(o.desc)}</div>

      <div class="tags">
        ${(o.tags||[]).slice(0,6).map(t=> `<span class="tag">#${escapeHtml(t)}</span>`).join("")}
      </div>

      <div class="card-foot">
        <div class="price">${money(o.price)}</div>
        <button class="btn btn-primary smallbtn" data-take="${o.id}" type="button">Взять</button>
      </div>
    </article>
  `).join("");

  $$("[data-take]", grid).forEach(btn=>{
    btn.addEventListener("click", ()=>{
      takeOrder(btn.dataset.take);
    });
  });
}

function renderMyOrders(){
  const my = load(STORAGE.myOrders, []);
  const box = $("#myOrdersList");
  if (!my.length){
    box.innerHTML = `<div class="muted">Пока пусто. Нажмите “Взять” на любом заказе.</div>`;
    return;
  }
  box.innerHTML = my.map(m => `
    <div class="my-item">
      <div class="my-left">
        <div class="my-title">${escapeHtml(m.title)}</div>
        <div class="my-meta">
          <span class="badge diff-${m.difficulty}">${escapeHtml(DIFF[m.difficulty]?.label ?? m.difficulty)}</span>
          <span class="badge">${money(m.price)}</span>
          <span class="badge">${escapeHtml(m.status)}</span>
        </div>
      </div>
      <div class="my-actions">
        <button class="btn btn-ghost smallbtn" data-status="${m.id}" data-v="В работе" type="button">В работе</button>
        <button class="btn btn-ghost smallbtn" data-status="${m.id}" data-v="Выполнен" type="button">Выполнен</button>
        <button class="btn btn-ghost smallbtn" data-status="${m.id}" data-v="Отменён" type="button">Отменён</button>
        <button class="btn btn-ghost smallbtn" data-remove="${m.id}" type="button">Удалить</button>
      </div>
    </div>
  `).join("");

  $$("[data-status]", box).forEach(b=>{
    b.addEventListener("click", ()=>{
      updateMyOrderStatus(b.dataset.status, b.dataset.v);
    });
  });
  $$("[data-remove]", box).forEach(b=>{
    b.addEventListener("click", ()=>{
      removeMyOrder(b.dataset.remove);
    });
  });
}

function takeOrder(orderId){
  const orders = load(STORAGE.orders, DEMO_ORDERS);
  const o = orders.find(x=>x.id===orderId);
  if (!o) return;
  const my = load(STORAGE.myOrders, []);
  if (my.some(x=>x.orderId===orderId)){
    toast("Этот заказ уже в вашем списке.");
    return;
  }
  my.unshift({
    id: uid(),
    orderId,
    title: o.title,
    difficulty: o.difficulty,
    price: o.price,
    status: "В работе",
    takenAt: Date.now()
  });
  save(STORAGE.myOrders, my);
  renderMyOrders();
  toast("Заказ добавлен в “Мои заказы”.");
}

function updateMyOrderStatus(id, status){
  const my = load(STORAGE.myOrders, []);
  const it = my.find(x=>x.id===id);
  if (!it) return;
  it.status = status;
  save(STORAGE.myOrders, my);
  renderMyOrders();
  toast("Статус обновлён.");
}

function removeMyOrder(id){
  const my = load(STORAGE.myOrders, []).filter(x=>x.id!==id);
  save(STORAGE.myOrders, my);
  renderMyOrders();
  toast("Удалено.");
}

/* AUTH */
function openModal(dlg){ if (!dlg.open) dlg.showModal(); }
function closeModal(dlg){ if (dlg.open) dlg.close(); }

function setTabs(active){
  $$(".tab").forEach(t=> t.classList.toggle("active", t.dataset.tab===active));
  $("#loginForm").classList.toggle("active", active==="login");
  $("#registerForm").classList.toggle("active", active==="register");
}

function handleLogin(e){
  e.preventDefault();
  const form = e.currentTarget;
  const login = (new FormData(form).get("login")||"").toString().trim();
  const password = (new FormData(form).get("password")||"").toString();

  // Owner login
  if (login === OWNER_LOGIN && password === OWNER_PASS){
    save(STORAGE.owner, { isOwner:true, at: Date.now() });
    save(STORAGE.user, { login, nick:"Owner", role:"owner", at: Date.now() });
    toast("Вход владельца выполнен.");
    closeModal($("#authModal"));
    return;
  }

  // Regular user login (local)
  const users = load("nw_users", {});
  const u = users[login];
  if (!u || u.password !== password){
    toast("Неверный логин или пароль.");
    return;
  }
  save(STORAGE.user, { login, nick:u.nick || login, role:"user", at: Date.now() });
  toast("Вы вошли.");
  closeModal($("#authModal"));
}

function handleRegister(e){
  e.preventDefault();
  const form = e.currentTarget;
  const fd = new FormData(form);
  const nick = (fd.get("nick")||"").toString().trim();
  const email = (fd.get("email")||"").toString().trim();
  const login = (fd.get("login")||"").toString().trim();
  const password = (fd.get("password")||"").toString();

  if (!login || !password){
    toast("Заполните логин и пароль.");
    return;
  }
  if (login === OWNER_LOGIN){
    toast("Этот логин зарезервирован.");
    return;
  }
  const users = load("nw_users", {});
  if (users[login]){
    toast("Такой логин уже существует.");
    return;
  }
  users[login] = { nick, email, password, createdAt: Date.now() };
  save("nw_users", users);
  save(STORAGE.user, { login, nick: nick || login, role:"user", at: Date.now() });
  toast("Аккаунт создан. Вы вошли.");
  closeModal($("#authModal"));
}

function logout(){
  localStorage.removeItem(STORAGE.user);
  localStorage.removeItem(STORAGE.owner);
  toast("Вы вышли.");
  closeModal($("#ownerModal"));
  updateOwnerPanel();
}

/* OWNER PANEL */
function updateOwnerPanel(){
  const locked = $("#ownerLocked");
  const content = $("#ownerContent");
  if (isOwner()){
    locked.hidden = true;
    content.hidden = false;
    renderOwnerOrders();
  } else {
    locked.hidden = false;
    content.hidden = true;
  }
}

function renderOwnerOrders(){
  const list = $("#ownerOrdersList");
  const search = ($("#ownerOrdersSearch")?.value || "").trim().toLowerCase();
  if (!list) return;

  if (!isOwner()){
    list.innerHTML = "";
    return;
  }

  const orders = load(STORAGE.orders, DEMO_ORDERS);
  let items = orders.slice();

  if (search){
    items = items.filter(o => {
      const hay = [o.id, o.title, o.desc, ...(o.tags||[])].join(" ").toLowerCase();
      return hay.includes(search);
    });
  }

  if (!items.length){
    list.innerHTML = `<div class="muted">Нет заказов для отображения.</div>`;
    return;
  }

  list.innerHTML = items.map(o => `
    <div class="owner-order-item">
      <div class="owner-order-main">
        <div class="owner-order-title">${escapeHtml(o.title)} <span class="muted small">(${escapeHtml(o.id)})</span></div>
        <div class="owner-order-desc">${escapeHtml(o.desc)}</div>
        <div class="owner-order-meta">
          <span class="badge diff-${o.difficulty}">${escapeHtml(DIFF[o.difficulty]?.label ?? o.difficulty)}</span>
          <span class="badge">${money(o.price)}</span>
          ${(o.tags||[]).slice(0,6).map(t=> `<span class="badge">#${escapeHtml(t)}</span>`).join("")}
        </div>
      </div>

      <div class="owner-order-actions">
        <button class="btn btn-ghost smallbtn" data-owner-delete="${escapeHtml(o.id)}" type="button">Удалить</button>
      </div>
    </div>
  `).join("");

  $$('[data-owner-delete]', list).forEach(btn=>{
    btn.addEventListener('click', ()=>{
      const id = btn.dataset.ownerDelete;
      if (!id) return;
      const ok = confirm('Удалить этот заказ? Он исчезнет из списка заказов.');
      if (!ok) return;
      deleteOrder(id);
    });
  });
}

function deleteOrder(orderId){
  if (!isOwner()){
    toast("Только владелец может удалять заказы.");
    return;
  }
  const before = load(STORAGE.orders, DEMO_ORDERS);
  const after = before.filter(o => o.id !== orderId);
  if (after.length === before.length){
    toast("Заказ не найден.");
    return;
  }
  save(STORAGE.orders, after);

  // also prune from local "My orders" if needed
  const my = load(STORAGE.myOrders, []);
  const myAfter = my.filter(m => m.orderId !== orderId);
  if (myAfter.length !== my.length){
    save(STORAGE.myOrders, myAfter);
  }

  renderOrders();
  renderMyOrders();
  renderOwnerOrders();
  toast("Заказ удалён.");
}

function addOrder(e){
  e.preventDefault();
  if (!isOwner()){
    toast("Только владелец может добавлять заказы.");
    return;
  }
  const fd = new FormData(e.currentTarget);
  const title = (fd.get("title")||"").toString().trim();
  const desc = (fd.get("desc")||"").toString().trim();
  const difficulty = (fd.get("difficulty")||"").toString();
  const price = Number((fd.get("price")||"").toString().replace(/\s/g,"").replace(",","."));

  if (!title || !desc || !difficulty || !Number.isFinite(price)){
    toast("Заполните все поля корректно.");
    return;
  }
  const tagsRaw = (fd.get("tags")||"").toString();
  const tags = tagsRaw.split(",").map(s=>s.trim()).filter(Boolean).slice(0, 10);

  const orders = load(STORAGE.orders, DEMO_ORDERS);
  orders.unshift({
    id: uid(),
    title,
    desc,
    difficulty,
    price,
    tags,
    _createdAt: Date.now()
  });
  save(STORAGE.orders, orders);
  e.currentTarget.reset();
  renderOrders();
  renderOwnerOrders();
  toast("Заказ добавлен.");
}

function resetOrders(){
  if (!isOwner()){ toast("Только владелец."); return; }
  save(STORAGE.orders, DEMO_ORDERS);
  renderOrders();
  renderOwnerOrders();
  toast("Сброшено к демо.");
}

function exportOrders(){
  if (!isOwner()){ toast("Только владелец."); return; }
  const orders = load(STORAGE.orders, DEMO_ORDERS);
  $("#ioBox").value = JSON.stringify(orders, null, 2);
  toast("Экспорт готов.");
}

function importFromText(){
  if (!isOwner()){ toast("Только владелец."); return; }
  const text = ($("#ioBox").value || "").trim();
  if (!text){ toast("Поле JSON пустое."); return; }
  const data = safeParse(text, null);
  if (!Array.isArray(data)){ toast("Неверный JSON (ожидается массив заказов)."); return; }
  save(STORAGE.orders, data);
  renderOrders();
  renderOwnerOrders();
  toast("Импорт применён.");
}

function clearIo(){
  $("#ioBox").value = "";
}

function handleImportFile(file){
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    $("#ioBox").value = String(reader.result || "");
    toast("Файл загружен в поле JSON.");
  };
  reader.readAsText(file);
}

/* HELPERS */
function escapeHtml(str){
  return String(str ?? "")
    .replaceAll("&","&amp;")
    .replaceAll("<","&lt;")
    .replaceAll(">","&gt;")
    .replaceAll("\"","&quot;")
    .replaceAll("'","&#039;");
}

/* INIT */
function init(){
  $("#year").textContent = String(new Date().getFullYear());

  ensureOrders();
  renderOrders();
  renderMyOrders();

  // theme
  applyThemeFromBg();

  // nav buttons
  $("#goOrdersBtn").addEventListener("click", ()=> smoothScrollTo("orders"));
  $("#heroTakeOrderBtn").addEventListener("click", ()=> smoothScrollTo("orders"));
  $("#howItWorksBtn").addEventListener("click", ()=> smoothScrollTo("how"));
  $("#howGoOrdersBtn").addEventListener("click", ()=> smoothScrollTo("orders"));
  $("#scrollTopBtn").addEventListener("click", ()=> smoothScrollTo("home"));

  // auth modal
  const authModal = $("#authModal");
  $("#openAuthBtn").addEventListener("click", ()=> openModal(authModal));
  $("#closeAuthBtn").addEventListener("click", ()=> closeModal(authModal));
  $("#continueGuestBtn").addEventListener("click", ()=> { closeModal(authModal); toast("Гостевой режим."); });
  $("#ownerHintBtn").addEventListener("click", ()=> openModal(authModal));

  // tabs
  $$(".tab").forEach(tab=>{
    tab.addEventListener("click", ()=> setTabs(tab.dataset.tab));
  });

  $("#loginForm").addEventListener("submit", handleLogin);
  $("#registerForm").addEventListener("submit", handleRegister);

  // orders controls
  ["searchInput","difficultySelect","sortSelect"].forEach(id=>{
    $("#"+id).addEventListener("input", renderOrders);
    $("#"+id).addEventListener("change", renderOrders);
  });

  // owner panel
  const ownerModal = $("#ownerModal");
  $("#openOwnerPanelBtn").addEventListener("click", ()=>{
    updateOwnerPanel();
    openModal(ownerModal);
  });
  $("#closeOwnerBtn").addEventListener("click", ()=> closeModal(ownerModal));
  $("#ownerGoLoginBtn").addEventListener("click", ()=>{
    closeModal(ownerModal);
    setTabs("login");
    openModal(authModal);
  });

  $("#addOrderForm").addEventListener("submit", addOrder);
  // owner orders search
  $("#ownerOrdersSearch")?.addEventListener("input", renderOwnerOrders);
  $("#resetOrdersBtn").addEventListener("click", resetOrders);
  $("#exportBtn").addEventListener("click", exportOrders);
  $("#applyImportBtn").addEventListener("click", importFromText);
  $("#clearIoBtn").addEventListener("click", clearIo);
  $("#logoutBtn").addEventListener("click", logout);
  $("#importFile").addEventListener("change", (e)=> handleImportFile(e.target.files?.[0]));

  // close on outside click for dialog (nice UX)
  [authModal, ownerModal].forEach(dlg=>{
    dlg.addEventListener("click", (e)=>{
      const r = dlg.getBoundingClientRect();
      const isIn = e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom;
      if (!isIn) closeModal(dlg);
    });
  });
}

document.addEventListener("DOMContentLoaded", init);
