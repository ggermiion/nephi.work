# TSUKAT plugin workspace

Рабочая ветка для последовательного ремонта кастомных плагинов TSUKAT.

## Оставляем как актуальную основу

1. **TsukatWindwing**
   - JAR: TsukatWindwing-2.1.1-RSPM-FIX
   - RP: TsukatWindwing-ResourcePack-2.1.0
   - Первый в очереди на полный ремонт.

2. **TsukatTab**
   - JAR: TsukatTab 1.0.0
   - RP: TsukatTab-ResourcePack
   - В JAR сейчас лежит тот же resourcepack.zip; при ремонте убираем embedded RP и оставляем только отдельный ZIP для ResourcePackManager.

3. **TsukatProfile**
   - JAR: TsukatProfile-1.1.2-RSPM
   - RP: TsukatProfile-ResourcePack-1.1.2
   - RP 1.1.1 и 1.1.2 побайтно одинаковы — старый 1.1.1 не нужен.

4. **TsukatCountdown**
   - JAR: TsukatCountdown-1.2.8-RSPM
   - RP: TsukatCountdown-ResourcePack-1.2.8

5. **TsukatAwakening**
   - JAR: TsukatAwakening-1.1.4-RSPM
   - RP: TsukatAwakening-ResourcePack-1.1.4

6. **TsukatRanks**
   - JAR: TsukatRanks 1.0.0
   - нужен как зависимость/интеграция для TAB.

## Не используем

- TsukatAwakening-1.1.2-FIXED
- TsukatAwakening-1.1.3-FORCEPACK
- 32.jar (точная копия TsukatAwakening-1.1.3-FORCEPACK)
- TsukatCountdown-1.2.7-Paper-26.2-timer-title-only
- TsukatProfile-1.1.1-FIXED
- TsukatProfile-ResourcePack-1.1.1
- ResourcePackManager.jar не модифицируем: это сторонняя зависимость.

## Правило ресурспаков

Каждый наш плагин: отдельный JAR + отдельный RP ZIP.
Сам JAR не отправляет, не хостит и не предлагает pack.
Все ZIP идут в `plugins/ResourcePackManager/mixer/`.
