# Synamyk Admin — ДЕЛЬТА: ОРТ-тест целиком, тексты, формулы и чертежи, продукты, школы, игры

> Дополнение к `ADMIN_FRONTEND_PROMPT.md` и `ADMIN_FRONTEND_DELTA_PROMPT.md`. Бэкенд готов (ветка `feature/ort-full-test`).
> Все эндпоинты — под `/api/admin/**`, роль ADMIN. Ошибки: `{ "success": false, "message": "..." }` — показывать `message`.
> Даты без зоны (Asia/Bishkek): `"2027-05-15T08:00:00"`.

---

## A. Тест и разделы

### A1. Тест — `POST /api/admin/tests`, `PUT /api/admin/tests/{id}`
Новое поле **`maxScore`** (int, по умолчанию 245) — максимальный балл ОРТ всего теста. В ответе `AdminTestResponse.maxScore`.

Подсказка в форме: «Тест проходится целиком: разделы идут подряд, результат — один балл по шкале ОРТ. Цена теста (`price`) = покупка всего теста».

### A2. Разделы (подтесты) — `POST /api/admin/tests/{testId}/sub-tests`, `PUT /api/admin/sub-tests/{id}`
Новые поля:
- **`maxScore`** (int | null) — сколько баллов ОРТ даёт раздел. `null` = автоматически: доля `test.maxScore`, пропорциональная сумме баллов вопросов раздела. Для стандартного ОРТ (Математика 1 и 2 по 30, Аналогии 30, Чтение 30, Грамматика 30) авто даёт Математика 1+2 = 98, остальные по 49, итого 245. Показывать в таблице разделов столбец «Баллы ОРТ» (`null` → «авто»).
- **`iconUrl`** — иконка раздела (загрузка через существующий `POST /api/upload?type=TEST_ICON`, в поле — ключ/URL).

Порядок разделов = `levelOrder`, длительность = `durationMinutes` (у каждого раздела свой таймер). Эталон ОРТ: Математика 1 — 30 мин/30 вопр., Математика 2 — 60/30, Аналогии и дополнение предложений — 30/30, Чтение и понимание — 60/30, Грамматика — 35/30; итого 215 мин, 150 вопросов.

---

## B. Тексты для чтения внутри раздела («Окуу жана түшүнүү»)

| Метод | Путь | Тело |
|---|---|---|
| GET | `/api/admin/sub-tests/{subTestId}/passages` | → `PassageResponse[]` |
| POST | `/api/admin/sub-tests/{subTestId}/passages` | `PassageRequest` |
| PUT | `/api/admin/passages/{passageId}` | `PassageRequest` |
| DELETE | `/api/admin/passages/{passageId}` | скрывает (soft) |

`PassageRequest`: `{ title?, titleKy?, text (обяз.), textKy?, imageUrl?, orderIndex }`.
`PassageResponse`: `{ id, subTestId, title, titleKy, text, textKy, imageUrl, orderIndex, active, questionCount }`.

UI: на странице раздела вкладка «Тексты». Большой textarea — **каждая строка на новой строке** (приложение нумерует каждую 5-ю). Счётчик привязанных вопросов.
В форме вопроса — select «Текст» (`passageId`) со списком текстов этого раздела.

---

## C. Редактор вопроса — новые поля `CreateQuestionRequest`

| Поле | Тип | Описание |
|---|---|---|
| `questionType` | `STANDARD` \| `COMPARISON` | тип вопроса |
| `columnA`, `columnAKy` | string | [COMPARISON] «Колонка А» |
| `columnB`, `columnBKy` | string | [COMPARISON] «Колонка Б» |
| `comparisonAnswer` | `A_GREATER` \| `B_GREATER` \| `EQUAL` \| `UNDETERMINED` | [COMPARISON] если `options` не переданы — сервер сам создаст 4 стандартных варианта (А. Колонка А больше / Б. Колонка Б больше / В. Равны / Г. Невозможно определить, с KY-переводом) и отметит правильный |
| `figure` | object \| null | чертёж (§D) |
| `passageId` | long \| null | привязка к тексту раздела |

- `options` теперь **необязательны** только для `COMPARISON` + `comparisonAnswer`. Иначе 2–6 вариантов и хотя бы один `isCorrect` — сервер теперь проверяет это и при **обновлении**.
- `COMPARISON` требует непустые `columnA` и `columnB` (400 иначе).
- `sectionName` = **тема** вопроса — из неё строится разбор «Темалар боюнча» в результате. Сделайте autocomplete по уже использованным темам раздела.
- Ответ `AdminQuestionResponse` содержит все новые поля (`figure` — объект).

**UI формы:**
1. Переключатель «Обычный / Сравнение (Колонка А–Б)».
2. Для сравнения: два поля с превью формулы рядом + радио «Правильно: А больше / Б больше / Равны / Нельзя определить» → `comparisonAnswer` (варианты не показывать; при редактировании существующего вопроса отправлять `options` из ответа сервера, чтобы не пересоздавать их).
3. **Формулы**: во всех текстовых полях LaTeX в `$...$` (строчная) и `$$...$$` (блочная). Под полем — живое превью (KaTeX). Панель быстрых вставок: дробь `\frac{a}{b}`, степень `x^{2}`, корень `\sqrt{x}`, `\pi`, `\le \ge \ne`, `\cdot`, `\times`, `^\circ`, `\angle`, `\triangle`, `\parallel \perp`. В JSON строке обратный слэш экранируется автоматически сериализатором.
4. Блок «Чертёж» (§D).

---

## D. Чертёж `figure` — редактор координатной плоскости и геометрии

Сервер валидирует структуру (400 «Некорректная фигура: …» с указанием поля). Тот же формат у вопросов **игр** (`CreateGameQuestionRequest.figure`).

```json
{ "type": "COORDINATE_PLANE", "xMin": -5, "xMax": 5, "yMin": -5, "yMax": 5, "gridStep": 1,
  "showGrid": true, "showAxes": true, "xLabel": "x", "yLabel": "y",
  "elements": [
    { "kind": "FUNCTION", "expression": "x^2 - 2*x + 1", "color": "#1976D2" },
    { "kind": "POINT", "x": 1, "y": 0, "label": "A" },
    { "kind": "SEGMENT", "from": [0, 0], "to": [3, 4], "label": "5", "dashed": true }
  ] }
```
```json
{ "type": "GEOMETRY",
  "elements": [
    { "kind": "POLYGON", "points": [[0,0],[4,0],[0,3]], "labels": ["A","B","C"], "fill": "#331976D2" },
    { "kind": "ANGLE", "vertex": [0,0], "from": [4,0], "to": [0,3], "right": true },
    { "kind": "SEGMENT", "from": [4,0], "to": [0,3], "label": "c" }
  ] }
```

Правила сервера:
- `type`: `COORDINATE_PLANE` (обязательны `xMin < xMax`, `yMin < yMax`; `gridStep > 0` если задан) или `GEOMETRY`.
- `elements` ≤ 300. Точка — массив `[x, y]` из чисел.
- `kind` и обязательные поля: `POINT/TEXT` — `x, y` (+ `text` у TEXT); `SEGMENT/LINE/RAY/VECTOR` — `from, to`; `POLYGON/POLYLINE` — `points` (≥2); `CIRCLE` — `center, radius>0`; `ARC` — `center, radius>0, startAngle, endAngle` (градусы); `ANGLE` — `vertex, from, to` (+ `right`, `label`); `FUNCTION` — `expression` (≤200 символов: `x`, числа, `+ - * / ^ ( )`, `sin cos tan cot sqrt abs log ln exp pi e`), опц. `xFrom < xTo`.
- `color`, `fill` — `#RRGGBB` или `#AARRGGBB`. Необязательные у всех: `label`, `dashed`.

**UI редактора (рекомендуется):**
- Слева форма: тип, диапазоны осей, шаг сетки, чекбоксы сетки/осей; список элементов с кнопками «+ Точка / Отрезок / Прямая / Луч / Вектор / Многоугольник / Окружность / Дуга / Угол / Функция / Текст», у каждого — поля по таблице, цвет, пунктир, подпись, удалить, вверх/вниз.
- Справа живое превью (SVG/canvas) — тот же алгоритм, что в приложении: X вправо, Y вверх; для GEOMETRY — вписать по bounding box с отступом 10%.
- Клик по превью в режиме «Точка» добавляет точку с координатами, округлёнными до `gridStep/2`.
- Кнопка «JSON» — показать/вставить сырой JSON.
- «Убрать чертёж» → `figure: null`.

---

## E. Продукты «Все тесты / Все тексты» и настройки

| Метод | Путь | Что |
|---|---|---|
| GET | `/api/admin/products` | `[{ code, title, description, price, oldPrice, available, owned, features }]` |
| PUT | `/api/admin/products/{ALL_TESTS\|ALL_TEXTS}` | `{ price, oldPrice?, active }` — `active=true` требует `price > 0` |
| PUT | `/api/admin/settings/ort-exam-date` | `{ "date": "2027-05-15T08:00:00" }` или `null` — счётчик «N күн M саат калды» в приложении |
| POST | `/api/admin/all-access` | `{ userId, product, durationDays?, expiresAt? }` — выдать вручную (без срока = бессрочно) |
| DELETE | `/api/admin/all-access?userId=&product=` | отозвать |

Страница «Продукты»: 2 карточки (цена, старая цена, переключатель «Продаётся»), ниже — дата ОРТ (datetime picker).
На карточке пользователя — кнопки «Выдать все тесты / все тексты».

Платежи: в списках `testTitle` для таких покупок = название продукта, `subTestId` = null.

---

## F. Библиотека «Тексттер»

| Метод | Путь | Что |
|---|---|---|
| GET | `/api/admin/texts` | все, включая скрытые: `{ id, title, titleKy, content, contentKy, pdfKey, pdfUrl, free, orderIndex, active, createdAt }` |
| POST | `/api/admin/texts/pdf` | multipart `file` (PDF ≤ 30 МБ) → `{ "key": "texts/uuid.pdf" }` |
| POST | `/api/admin/texts` | `{ title, titleKy?, content?, contentKy?, pdfUrl?, free, orderIndex, active }` — нужен `content` **или** `pdfUrl` |
| PUT | `/api/admin/texts/{id}` | то же |
| DELETE | `/api/admin/texts/{id}` | скрыть |

UI: таблица с сортировкой drag&drop (`orderIndex`), флаг «Бесплатный» (виден без покупки), загрузка PDF → ключ в `pdfUrl`, ссылка «Открыть PDF» (`pdfUrl` в ответе — временная).

---

## G. Районы и школы (для рейтинга школ)

| Метод | Путь | Что |
|---|---|---|
| GET | `/api/regions` | регионы (публично) |
| GET | `/api/admin/regions/{regionId}/districts` | районы, включая неактивные: `{ id, regionId, name, nameKy, active }` |
| POST | `/api/admin/districts` | `{ regionId, name, nameKy?, active }` |
| PUT | `/api/admin/districts/{id}` | то же |
| GET | `/api/admin/districts/{districtId}/schools?search=&active=&page=&size=` | Page `{ id, districtId, name, nameKy, active }` |
| POST | `/api/admin/schools` | `{ districtId, name, nameKy?, active }` |
| POST | `/api/admin/schools/bulk` | `{ districtId, names: ["...", "..."] }` → `{ "created": N }` (дубли по названию пропускаются) |
| PUT | `/api/admin/schools/{id}` | то же; `active=false` скрывает |

UI: страница «Школы» — регион → район (список + добавить/редактировать) → таблица школ с поиском. Кнопка «Импорт списком»: textarea «по одной школе на строку» → `bulk`.
Правило приложения: школа попадает в рейтинг, когда в ней **≥ 3** зарегистрированных ученика.

---

## H. Игры

- `CreateGameQuestionRequest` / `GameTestResponse.QuestionDetail` — новое поле **`figure`** (формат §D). Текст и варианты поддерживают LaTeX — добавьте превью формул и редактор чертежа, как в §C/§D.
- Порядок вопросов **и вариантов** перемешивается сервером в каждой игре — `orderIndex` на игроков не влияет, подсказку в UI поправить.
- В приложении появился игровой рейтинг (Эло); в админке отдельных эндпоинтов нет.

---

## I. Чек-лист
- [ ] `maxScore` у теста и раздела, иконка раздела.
- [ ] Вкладка «Тексты» раздела, привязка вопроса к тексту.
- [ ] Вопрос-сравнение с авто-вариантами, темы с autocomplete.
- [ ] LaTeX-превью во всех полях вопроса и игры.
- [ ] Редактор чертежа с живым превью, для тестов и игр.
- [ ] Продукты, дата ОРТ, ручная выдача «все тесты/тексты».
- [ ] Библиотека текстов с PDF.
- [ ] Районы и школы с импортом списком.
