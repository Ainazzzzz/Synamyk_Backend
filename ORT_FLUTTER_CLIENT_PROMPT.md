# Synamyk Flutter — ТЗ: ОРТ-тест целиком, баллы, «купить всё», тексты, рейтинг школ, «пригласи друга», игровой рейтинг, формулы и графики

> Задача для Claude Code по **Flutter-клиенту** Synamyk. Бэкенд уже готов (ветка `feature/ort-full-test`).
> Ориентир по UI — приложение-аналог «Доор платформасы» (скриншоты у заказчика): синяя шапка, белые карточки со скруглением, нижняя навигация **Негизги / Рейтинг / Жыйынтык / Профиль**.
> Все тексты API приходят на языке пользователя (RU/KY). Ошибки: `{ "success": false, "message": "<локализовано>" }` — показывать `message` как есть.
> Даты — без зоны, Asia/Bishkek: `"2026-09-16T19:40:00"`.

---

## 0. Что сделать (кратко)

1. **Главная «Тесттер»**: счётчик до ОРТ, баннер «Купить все тесты», карточки тестов с бейджем «Акысыз» / «Премиум» и временем «03 саат 35 мүн».
2. **Карточка теста**: общее время + число вопросов, список разделов (иконка, «30 мүн · 30 суроо»), кнопка «Баштоо». Если есть незавершённая попытка — диалог **«Продолжить / Начать заново»**.
3. **Прохождение теста целиком**: разделы подряд, у каждого свой таймер, все вопросы раздела одним списком, свободный выбор/смена ответа, тексты для чтения с нумерацией строк, вопросы «Колонка А / Колонка Б», формулы LaTeX, графики и фигуры.
4. **Результат**: балл ОРТ (из 245), «Бөлүктөр боюнча» (баллы и % по разделам), «Темалар боюнча», кнопка «Бөлүшүү».
5. **«Тексттер»**: список текстов с замками, экран оплаты «Окуу тексттерин ач», просмотр текста/PDF.
6. **Рейтинг**: школы района (Упай / Активдүүлүк / Өсүш), районы, ученики; карточка «Рейтингге кирүүгө: 1/3 окуучу» + «Досторду чакыруу».
7. **Пригласи друга**: код, шаринг, награды → открыть любой тест бесплатно.
8. **Регистрация/профиль**: выбор района и школы, поле «Код друга».
9. **Игры**: игровой рейтинг (Эло), лига, изменение рейтинга после игры, таблица лидеров; варианты ответов приходят уже перемешанными.
10. **Рендер формул и чертежей** — в тестах и в играх.

---

## 1. Главная — вкладка «Тесттер»

### 1.1 `GET /api/app/config` (без токена)
```json
{ "ortExamDate": "2027-05-15T08:00:00", "secondsUntilExam": 20995200, "ortMaxScore": 245, "ortThresholdScore": 110, "schoolRatingMinStudents": 3 }
```
Карточка «Жалпы республикалык тестирлөөгө **242 күн 4 саат калды**». Считайте от `secondsUntilExam` локально (тикает раз в минуту). `ortExamDate == null` → карточку скрыть.

### 1.2 `GET /api/products`
```json
[ { "code": "ALL_TESTS", "title": "Открыть все тесты", "description": "...", "price": 990.00, "oldPrice": null,
    "available": true, "owned": false, "features": ["...", "...", "..."] },
  { "code": "ALL_TEXTS", ... } ]
```
Баннер «🔒 Бир жолку төлөм менен бардык тесттерди ачыңыз» + кнопка «Тесттерди ачуу» — только если `ALL_TESTS.available && !owned`. Крестик скрывает баннер (запомнить локально).

### 1.3 `GET /api/tests` — список
Новые поля `TestListResponse`:

| Поле | Использование |
|---|---|
| `totalDurationMinutes` | «03 саат 35 мүн» |
| `totalQuestions` | |
| `maxScore` | 245 |
| `isFree` | бейдж «✓ Акысыз» (зелёный) |
| `hasAccess` | `false` → бейдж «🔒 Премиум»; тап → экран оплаты теста |
| `bestOrtScore` | лучший балл, `null` если не проходил |
| `completedAttempts` | |
| `resumableAttemptId` | есть незавершённая попытка → на карточке «Продолжить» |

Порядок бейджей: `isFree` → «Акысыз»; иначе `hasAccess` → без бейджа (или «Ачык»); иначе «Премиум».

---

## 2. Карточка теста — `GET /api/tests/{testId}`

Новые поля: `iconUrl, isFree, hasAccess, totalDurationMinutes, totalQuestions, maxScore, bestOrtScore, resumableAttemptId, resumableSectionIndex`.
В `subTests[]` новые: `iconUrl`, `maxScore` (баллы ОРТ раздела).

UI (скрин «Негизги тест 1»): верх — «⏱ 215 мүн» и «☰ 150 суроо»; ниже карточки разделов с иконкой, названием и «30 мүн · 30 суроо»; внизу «▶ Баштоо».

**Кнопка «Баштоо»:**
1. `hasAccess == false` → экран оплаты теста (`POST /api/payments/init?testId=`) **или** «Купить все тесты». Если у пользователя есть награды за друзей (`GET /api/referrals/me` → `availableRewards > 0`) — показать третий вариант «Открыть бесплатно за друга» (§7).
2. `resumableAttemptId != null` → диалог: **«Продолжить»** (`restart:false`) / **«Начать заново»** (`restart:true`).
3. Иначе → `POST /api/tests/{id}/attempts` с `{ "restart": false }`.

Старые экраны «подтест отдельно» (`/api/sub-tests/{id}/start`) оставить только как «Тренировка раздела», если они уже есть. Основной сценарий — тест целиком.

---

## 3. Прохождение теста целиком

### 3.1 Эндпоинты

| Метод | Путь | Что |
|---|---|---|
| GET | `/api/tests/{testId}/attempts/resumable` | 200 → есть незавершённая попытка (`AttemptStateResponse`); 204 → нет |
| POST | `/api/tests/{testId}/attempts` | body `{ "restart": false\|true }` → `AttemptStateResponse` |
| GET | `/api/attempts/{attemptId}` | состояние попытки |
| GET | `/api/attempts/{attemptId}/section` | текущий раздел со **всеми** вопросами (`SectionQuestionsResponse`). Первый вызов запускает таймер раздела |
| PUT | `/api/attempts/{attemptId}/answers` | `{ "questionId": 1, "selectedOptionIds": [3] }` — сохранить/сменить; `[]` — снять ответ |
| POST | `/api/attempts/{attemptId}/section/finish` | завершить раздел → следующий; после последнего `status = COMPLETED` |
| POST | `/api/attempts/{attemptId}/pause` | пауза: таймер раздела замирает |
| POST | `/api/attempts/{attemptId}/finish` | завершить весь тест досрочно → сразу `AttemptResultResponse` |
| GET | `/api/attempts/{attemptId}/result` | результат |
| GET | `/api/attempts/history?page=0&size=20` | вкладка «Жыйынтык» |

### 3.2 `AttemptStateResponse`
```json
{ "attemptId": 12, "testId": 1, "testTitle": "Негизги тест 1", "status": "IN_PROGRESS", "isResumed": false,
  "startedAt": "...", "totalDurationMinutes": 215, "totalQuestions": 150, "maxScore": 245,
  "currentSectionIndex": 0, "totalSections": 5, "totalRemainingSeconds": 12900,
  "sections": [ { "index": 0, "subTestId": 3, "title": "Математика 1", "iconUrl": "...", "durationMinutes": 30,
                  "questionCount": 30, "status": "NOT_STARTED", "answeredCount": 0, "remainingSeconds": 1800 } ] }
```
`sections[].status`: `NOT_STARTED | IN_PROGRESS | PAUSED | COMPLETED | EXPIRED`.

### 3.3 `SectionQuestionsResponse`
```json
{ "attemptId": 12, "sessionId": 40, "sectionIndex": 3, "totalSections": 5, "isLastSection": false,
  "subTestId": 6, "title": "Окуу жана түшүнүү 1", "durationMinutes": 60, "expiresAt": "...",
  "remainingSeconds": 3589, "totalRemainingSeconds": 5689, "totalQuestions": 30, "answeredCount": 0,
  "passages": [ { "id": 2, "number": 1, "title": "1-текст", "text": "строка 1\nстрока 2\n...", "imageUrl": null, "questionIds": [101, 102, 103] } ],
  "questions": [ {
      "questionId": 101, "index": 0, "number": 1, "passageId": 2, "sectionName": "Окуу",
      "questionType": "STANDARD", "text": "Автор жалгыздыкты эмне менен салыштырат?",
      "columnA": null, "columnB": null, "imageUrl": null, "figure": null, "pointValue": 1,
      "options": [ { "id": 501, "label": "А", "text": "Суусоо менен", "orderIndex": 0 } ],
      "selectedOptionIds": [] } ] }
```

### 3.4 Экран раздела
- **Шапка**: прогресс-бар `answeredCount / totalQuestions` («0 / 30») и таймер `⏱ 29:57` от `remainingSeconds`. Таймер тикает локально; раз в ~60 с и при возврате в приложение сверяйтесь с `GET /section`.
- **Список**: все вопросы одним скроллом, карточка «N-суроо».
- **Тексты для чтения**: для каждого `passages[]` — блок текста; строки делятся по `\n`; **номер у каждой 5-й строки** слева (5, 10, 15… как «110», «115» на скрине); текст по ширине. Под ним заголовок «**{number}-тексттин суроолору**» и вопросы из `questionIds` по порядку. Вопросы без `passageId` — обычным списком.
- **Тап по варианту** → оптимистично выделить → `PUT /answers`. Повторный тап по выбранному → `selectedOptionIds: []`. Выбор одиночный, как на ОРТ: новый тап заменяет прежний ответ.
- **Кнопка внизу**: «Кийинки бөлүм» (или «Аяктоо» при `isLastSection`) → подтверждение «Бөлүмдү аяктайсызбы? Кайра кайтууга болбойт» → `POST /section/finish`. Если `status == COMPLETED` → экран результата, иначе `GET /section` (следующий раздел).
- **Таймер дошёл до 0** → `GET /section`: бэк сам закроет раздел и вернёт следующий (или 400 «Тест уже завершён» → `GET /attempts/{id}` → если `COMPLETED`, открыть результат).
- **400 на `PUT /answers`** с «Время раздела истекло» → откатить выделение, `GET /section`.
- **Выход** (кнопка «назад» → диалог «Тестти токтотосузбу?») и **сворачивание приложения** (`AppLifecycleState.paused/detached`) → `POST /pause`. При возврате — `GET /section` снимет паузу.
- Меню «Завершить тест досрочно» → `POST /finish` → результат.

### 3.5 Вопрос-сравнение (`questionType == "COMPARISON"`, «Математика 1»)
Как на скрине: над вариантами два одинаковых бокса «КОЛОНКА А» | «КОЛОНКА Б» с содержимым `columnA` / `columnB` (LaTeX), варианты — обычные (А. Колонка А чоң / Б. Колонка Б чоң / В. Тең / Г. Аныктоо мүмкүн эмес). `text` может быть пустым или содержать условие — показать над колонками, если не пустой.

---

## 4. Результат — `AttemptResultResponse`
```json
{ "attemptId": 12, "testTitle": "Негизги тест 1", "userFullName": "Айназик Токтомаматова", "completedAt": "...",
  "ortScore": 163, "maxScore": 245, "thresholdScore": 110, "passedThreshold": true,
  "totalQuestions": 150, "correctAnswers": 98, "wrongAnswers": 40, "skippedAnswers": 12,
  "earnedPoints": 98, "totalPoints": 150, "percentage": 65.3, "timeTakenSeconds": 9120, "motivationalMessage": "...",
  "sections": [ { "subTestId": 3, "title": "Математика 1", "sessionId": 40, "status": "COMPLETED",
                  "totalQuestions": 30, "correctAnswers": 20, "wrongAnswers": 8, "skippedAnswers": 2,
                  "earnedPoints": 20, "totalPoints": 30, "percentage": 66.7, "ortScore": 33, "maxScore": 49 } ],
  "topics": [ { "topic": "Сөз түркүмдөрү", "subTestId": 7, "sectionTitle": "Грамматика 1", "totalQuestions": 1, "correctAnswers": 0, "percentage": 0.0 } ] }
```
UI (скрины «Жыйынтык»):
1. Карточка-диплом: кубок, **`ortScore` крупно**, «балл», ФИО, «{testTitle} · {dd.MM.yyyy}», подпись «Менин ЖРТ боюнча жетишкендигим», App Store-бейдж / instagram / сайт (статика). Под баллом маленько: «из {maxScore} · порог {thresholdScore}» и зелёная/красная метка `passedThreshold`.
2. «**Бөлүшүү**» — скриншот карточки (`RepaintBoundary` → PNG) + `share_plus`.
3. «**Темалар боюнча**» — таблица: Бөлүктөр | Жалпы суроолор | Туура жооптор | Пайызы.
4. «**Бөлүктөр боюнча**» — строки: название, «ortScore / maxScore балл» или «correct / total», процент, прогресс-бар.
5. Сноска «* Тесттин жыйынтыгы сабактар үчүн баа катары колдонулбайт».
6. Разбор ошибок раздела: `sections[].sessionId` → уже существующие `GET /api/sessions/{sessionId}/result` и `POST /api/sessions/{sessionId}/analyze-errors`.

**Вкладка «Жыйынтык»** — `GET /api/attempts/history`: карточки `testTitle`, `ortScore / maxScore`, `percentage`, дата → тап открывает результат.

> Как считается балл: у теста `maxScore` (245). Он делится между разделами (по умолчанию пропорционально числу баллов/вопросов: Математика 60 вопросов → 98, остальные разделы по 30 → по 49). Внутри раздела — линейно: `ortScore = round(earned / total × maxScore раздела)`. Официальная таблица ЦООМО не публикуется, это приближение по публичной шкале.

---

## 5. «Тексттер» + оплата продуктов

- `GET /api/texts` → `[{ id, title, orderIndex, free, hasAccess, hasPdf }]`. Список «📖 Текст 1 🔒». Баннер «Бир жолку төлөм менен бардык окуу тексттерин ачыңыз» + «Тексттерди ачуу», если `ALL_TEXTS.available && !owned`.
- Тап: `hasAccess` → `GET /api/texts/{id}` → `{ title, content, pdfUrl }`: показать `content` (если есть) и/или PDF (`pdfUrl` действует 1 час — открывать сразу, не кэшировать ссылку); иначе → экран оплаты.
- **Экран оплаты продукта** (скрин «Төлөм»): кубок, `title`, `description`, карточка «Бир жолку төлөм / **{price} сом**» (+ зачёркнутая `oldPrice`), галочки из `features[]`, кнопка «🔒 Төлөмгө өтүү», подпись «Коопсуз төлөм · QR, банк тиркемелери, VISA».
- Оплата: `POST /api/payments/init?product=ALL_TESTS` (или `ALL_TEXTS`) → тот же flow Finik SDK и polling `GET /api/payments/{paymentId}/status`, что и для тестов. После `COMPLETED` → обновить `/api/products`, `/api/tests`, `/api/texts`.
- Параметры `init`: **ровно один** из `testId` / `subTestId` / `product`.
- `GET /api/payments/my`: в записи новое поле `product` (`TEST | SUB_TEST | ALL_TESTS | ALL_TEXTS`); `testId` может быть `null` — тогда `testTitle` уже содержит название продукта.

---

## 6. Формулы и чертежи (тесты и игры)

### 6.1 Формулы
Любые тексты вопроса, `columnA/columnB`, вариантов, текстов для чтения могут содержать LaTeX: `$...$` — строчная формула, `$$...$$` — блочная. Рендер: `flutter_math_fork` (или `flutter_tex`). Разбивайте строку на куски текст/формула; при ошибке парсинга показывайте исходный текст.

### 6.2 Чертёж `figure` (JSON, может быть `null`)
Рисовать `CustomPainter` в квадратной области (ширина карточки, `AspectRatio` 1 для плоскости).

**Координатная плоскость** — X горизонтально, Y вертикально:
```json
{ "type": "COORDINATE_PLANE", "xMin": -5, "xMax": 5, "yMin": -5, "yMax": 5, "gridStep": 1,
  "showGrid": true, "showAxes": true, "xLabel": "x", "yLabel": "y",
  "elements": [ ... ] }
```
**Геометрия** (без осей; область вписать по bounding box всех точек с отступом 10%):
```json
{ "type": "GEOMETRY", "elements": [ ... ] }
```
`showGrid`/`showAxes` по умолчанию `true` для плоскости. Подписи делений осей — каждые `gridStep`.

**Элементы** (`kind`), точки — `[x, y]`; необязательные у всех: `label` (текст/LaTeX), `color` (`#RRGGBB` или `#AARRGGBB`), `dashed` (bool):

| kind | Поля | Рисование |
|---|---|---|
| `POINT` | `x, y` | закрашенный кружок r=4, `label` рядом |
| `TEXT` | `x, y, text` | подпись в точке |
| `SEGMENT` | `from, to` | отрезок; `label` у середины (длина) |
| `LINE` | `from, to` | прямая через 2 точки до краёв области |
| `RAY` | `from, to` | луч из `from` через `to` |
| `VECTOR` | `from, to` | отрезок со стрелкой |
| `POLYGON` | `points[]`, `fill?`, `labels?[]` | замкнутый многоугольник, `labels[i]` у вершины i |
| `POLYLINE` | `points[]` | ломаная |
| `CIRCLE` | `center, radius`, `fill?` | окружность |
| `ARC` | `center, radius, startAngle, endAngle` (градусы, против часовой от оси X) | дуга |
| `ANGLE` | `vertex, from, to`, `right?` | дуга угла между лучами vertex→from и vertex→to; `right: true` — квадратик прямого угла; `label` (напр. «30°») |
| `FUNCTION` | `expression`, `xFrom?`, `xTo?` | график y = f(x); переменная `x`; операции `+ - * / ^`, функции `sin cos tan cot sqrt abs log ln exp`, константы `pi e`. Парсинг — `math_expressions`. Шаг ~ (xMax−xMin)/400, разрывы (NaN/∞/скачок > высоты области) не соединять |

Порядок отрисовки: сетка → оси → `POLYGON/CIRCLE` заливки → линии → точки → подписи.

---

## 7. «Пригласи друга»

- `GET /api/referrals/me`:
```json
{ "code": "2T9KES", "inviteLink": "https://synamyk.kg/invite/2T9KES", "shareText": "Готовься к ОРТ вместе со мной! ...",
  "invitedCount": 3, "purchasedCount": 1, "availableRewards": 1, "canApplyCode": false, "referredByName": "Айбек Т.",
  "rewards": [ { "rewardId": 5, "friendName": "Нурлан А.", "earnedAt": "...", "available": true, "redeemedTestId": null, "redeemedTestTitle": null, "redeemedAt": null } ] }
```
- Экран: объяснение «Досуң тест сатып алса — сага каалаган 1 тест акысыз ачылат», код крупно + копировать, «Бөлүшүү» (`shareText`), счётчики «Чакырылды: 3 · Сатып алды: 1», список наград.
- `availableRewards > 0` → кнопка «Тестти акысыз ачуу» → выбор из тестов, где `hasAccess == false` → `POST /api/referrals/rewards/redeem { "testId": 7 }` → обновить `/api/tests`.
- `canApplyCode == true` → поле «Досуңдун коду» → `POST /api/referrals/apply { "code": "..." }`. Ошибки (свой код, уже применён, после покупки) — показать `message`.
- Push о награде приходит с `dataType = NONE` — при тапе открыть экран «Пригласи друга».
- Кнопка «Досторду чакыруу» на экране рейтинга школ ведёт сюда.

---

## 8. Регистрация и профиль

- `POST /api/auth/complete-profile` — новые **необязательные** поля: `schoolId`, `referralCode`. Шаги UI: регион → район `GET /api/regions/{regionId}/districts` → школа `GET /api/districts/{districtId}/schools?search=&page=0&size=50` (поиск с debounce) → поле «Код друга (необязательно)». Эти 2 списка доступны без токена.
- `GET /api/profile` — новые поля: `districtId, districtName, schoolId, schoolName, referralCode, gameRating`.
- Смена школы: `GET /api/profile/school` (204 — не выбрана) и `PUT /api/profile/school { "schoolId": 5 }` (регион подтянется автоматически).
- В профиле пункт «Пригласи друга».

---

## 9. Рейтинг (вкладка «Рейтинг»)

Сверху переключатель: **Мектептер** | **Райондор** | **Окуучулар** (| **Оюндар**, §10).

### 9.1 Школы — `GET /api/rating/schools?sort=SCORE|ACTIVITY|GROWTH[&districtId=]`
Без `districtId` — район школы пользователя; если школа не выбрана → 400 → показать «Выберите школу» с кнопкой на выбор школы.
```json
{ "districtId": 4, "districtName": "Октябрь району", "sort": "SCORE", "minStudents": 3,
  "mySchool": { "schoolId": 9, "schoolName": "...", "studentCount": 1, "minStudents": 3, "inRating": false, "studentsNeeded": 2, ... },
  "entries": [ { "rank": 1, "schoolId": 2, "name": "Школа №2", "studentCount": 5, "inRating": true, "value": 163.4, "isMine": false },
               { "rank": null, "schoolId": 9, "name": "...", "studentCount": 1, "inRating": false, "value": null, "isMine": true } ] }
```
UI (скрин «Мектептер рейтинги»): заголовок `districtName`, подзаголовок «Мелдеш жаңы башталууда — биринчилерден бол!»; если `mySchool && !mySchool.inRating` — карточка «Рейтингге кирүүгө: {studentCount}/{minStudents} окуучу», прогресс-бар, «Дагы {studentsNeeded} классташың керек — чакырып, мектебиңди рейтингге кошуп кой!», кнопка «Досторду чакыруу» (§7). Табы «Упай / Активдүүлүк / Өсүш» = `sort`. Строки: `rank` (или иконка группы, если `null`), название, для `inRating` — `value` («163 балл» / «12 тест» / «+8 балл»), иначе серым прогресс «n/3 окуучу». `isMine` — подсветить рамкой.

### 9.2 Районы — `GET /api/rating/districts[?regionId=]`
`entries[]: { rank, districtId, name, studentsWithResults, averageScore, isMine }`.

### 9.3 Ученики — `GET /api/rating/students?scope=SCHOOL|DISTRICT|REGION|ALL`
`{ scope, entries: [{ rank, userId, fullName, avatarUrl, schoolName, score, isMe }], me }` — топ-100, внизу закреплённая строка `me`, если он не в топе.

---

## 10. Игры — рейтинг и мотивация

- **Варианты ответа** в `NEXT_QUESTION` уже в случайном порядке, вопросы тоже — **не сортировать** на клиенте. В `question` новое поле `figure` (§6.2); `text` и варианты могут содержать LaTeX.
- **Нет соперника 15 сек** → бот (сила случайная, может и выиграть, и проиграть). Экран поиска: «Соперник изделүүдө…» с таймером.
- **`GAME_OVER`** — новые поля: `player1RatingChange`, `player2RatingChange` (null для бота), `player1Rating`, `player2Rating`. На экране итога: «+12» зелёным / «−9» красным, новый рейтинг, анимация счётчика.
- `GET /api/game/rating/me` → `{ rating, peakRating, rank, gamesPlayed, wins, losses, draws, winStreak, league, leagueName, nextLeagueRating }`. На главной игр: карточка рейтинга, лига (BRONZE < 1100 ≤ SILVER < 1300 ≤ GOLD < 1500 ≤ PLATINUM < 1700 ≤ DIAMOND), прогресс до `nextLeagueRating`, серия побед 🔥.
- `GET /api/game/leaderboard?page=0&size=50` → Page с тем же объектом (`fullName`, `avatarUrl`, `rating`, `rank`, `leagueName`). Вкладка «Оюндар» в рейтинге.
- `GET /api/game/history` — новые `ratingChange`, `ratingAfter`.
- Правила: старт 1000; победа +, поражение −, ничья ≈0; победа над сильным даёт больше; игры с ботом с половинным коэффициентом; сдача = поражение; минимум 100.

---

## 11. Чек-лист приёмки
- [ ] Бейджи Акысыз/Премиум и время «03 саат 35 мүн» на главной.
- [ ] Диалог «Продолжить / Начать заново» при незавершённой попытке; после закрытия приложения попытка продолжается с того же раздела и тех же ответов.
- [ ] Таймер раздела, автопереход при 0, пауза при сворачивании.
- [ ] Тексты с номерами строк; «N-тексттин суроолору».
- [ ] Колонка А / Колонка Б, LaTeX, координатная плоскость и фигуры.
- [ ] Результат: балл ОРТ, разделы с баллами и %, темы, «Бөлүшүү» картинкой.
- [ ] Покупка всех тестов / всех текстов; тексты с PDF.
- [ ] Рейтинг школ (3 таба), районы, ученики; карточка «1/3 окуучу».
- [ ] Реферальный код при регистрации, экран «Пригласи друга», открытие теста за награду.
- [ ] Игровой рейтинг, изменение после игры, таблица лидеров.
