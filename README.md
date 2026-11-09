<h1 align="center">Feral Лаунчер</h1>

<p align="center">
  Один лаунчер настроек для Android-портов Feral Interactive:<br>
  <b>Saints Row: The Third</b>, <b>Tomb Raider</b>, <b>Hitman: Absolution</b>, <b>GRID Legends</b>.
</p>

> **English:** Feral Launcher is a single Android app with per-game settings screens for the Feral Interactive ports of
> *Saints Row: The Third*, *Tomb Raider*, *Hitman: Absolution* and *GRID Legends*. It edits each game's preferences file
> through [Shizuku](https://shizuku.rikka.app/) or root. It does not modify the game APKs.

## Что умеет

- Главный экран с выбором игры; у каждой игры своя тема и свой набор настроек.
- Разрешение рендера, качество графики по пунктам, лимит FPS, динамическое разрешение, пресеты и подбор под железо.
- Скрытые параметры движка, которых нет в меню игры.
- Работает через root (Magisk, KernelSU, APatch) или Shizuku. Для Shizuku в карточке «Первичная настройка» есть кнопка быстрого перехода и команда ADB для выдачи доступа.
- Проверка обновлений через GitHub Releases.
- Не меняет APK игр.

## Переход со старых лаунчеров

Этот лаунчер заменяет отдельные приложения
[sr3-launcher](https://github.com/Tsifrokayf/sr3-launcher),
[tomb-raider-launcher](https://github.com/Tsifrokayf/tomb-raider-launcher) и
[hitman-launcher](https://github.com/Tsifrokayf/hitman-launcher).
SR3 Лаунчер обновится поверх (тот же пакет и подпись); лаунчеры Tomb Raider и Hitman придётся удалить вручную после установки.

## Сборка

```
gradle assembleRelease
```

Для своей подписи положите `launcher.jks` рядом с папкой `app` и задайте переменную `KS_PASS`; иначе релиз подписывается отладочным ключом.

## Лицензия

См. [LICENSE](LICENSE).
