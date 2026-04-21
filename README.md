# RealisticBodyHealth

`RealisticBodyHealth` is a `BodyHealth` addon for `Paper 1.21.x` running on `Java 21` to `Java 26`.

The addon keeps vanilla hearts out of survival logic and lets `BodyHealth` remain the main source of truth for body-part damage.

## Что делает аддон

- Оставляет внешний урон от мобов, игроков, стрел, зелий и блоков на стороне `BodyHealth`, но не дает ванильным сердечкам тратиться.
- Перенаправляет внутренний и нелокационный урон прямо в `TORSO`.
  - Сюда входят голод, утопление, огонь, лава и другие причины урона без конкретной точки попадания.
- Не дублирует смерть по `HEAD` и `TORSO`.
  - Смерть при нуле головы или торса должна настраиваться в самом `BodyHealth`.
- Оставляет кровотечение от сломанных конечностей и продолжает переводить его в постепенный урон по `TORSO`.
- Поддерживает `OP`-игроков.
  - По умолчанию аддон работает и на операторах, даже если они автоматически имеют bypass-права.
- Возвращает лечение частей тела от:
  - зелья мгновенного исцеления
  - зелья регенерации
  - других `EntityRegainHealthEvent`, которые теперь конвертируются в лечение частей тела, а не сердечек
- Лечит части тела после сна только если ночь реально была пропущена.

## Визуальные эффекты

- При кровотечении остаются эффекты крови, звуки и actionbar.
- При низком здоровье `HEAD` или `TORSO` появляется красная виньетка через персональный `WorldBorder` и дополнительные красные частицы.

## Совместимость

- `Paper 1.21.x`
- `Java 21` - `Java 26`
- `BodyHealth 4.1.0`

`RealisticBodyHealth` автоматически принудительно выключает `plugins/BodyHealth/config.yml -> heal-on-full-health`, потому что эта настройка ломает механику аддона.

## Сборка

```bash
mvn package
```

Готовый jar создаётся в `target/` как `RealisticBodyHealth-<version>.jar`.

## Установка

1. Собери jar через Maven или возьми готовый релиз.
2. Положи jar **не** в `/plugins`.
3. Правильный путь для аддона: `/plugins/BodyHealth/addons/`
4. Перезапусти сервер или перезагрузи `BodyHealth`.

## Ресурспак на скрытие сердечек

В репозитории уже лежит готовая папка [resourcepack](resourcepack/) с текстурами для скрытия ванильных сердечек.

Если ты хочешь полностью убрать hearts HUD:

1. Заархивируй содержимое папки `resourcepack/` в zip.
2. Подключи этот архив как обычный серверный ресурспак.
3. Показывай здоровье через `BetterHud` / `BodyHealth`.

Также остаётся шаблон документации в [docs/resource-pack-template/README.md](docs/resource-pack-template/README.md).

## Конфиг

Основные настройки:

- `apply-to-operators`
  - включает механику аддона для `OP`
- `bleeding.*`
  - скорость и летальность кровотечения
- `critical-effects.*`
  - пороги и интенсивность визуальных эффектов для `HEAD` и `TORSO`
- `sleep-healing.heal-percent-per-part`
  - сколько процентов здоровья части тела восстанавливается за одну реально пропущенную ночь

## Права

- `realisticbodyhealth.bypass`
- Уважаются права `bodyhealth.bypass.*`
- Уважаются права `bodyhealth.bypass.damage.*`
- Уважаются права `bodyhealth.bypass.regen.*`
- Уважаются `bodyhealth.bypass.damage.<part>` и `bodyhealth.bypass.regen.<part>`

Если `apply-to-operators: true`, операторы не будут автоматически обходить механику аддона только из-за `OP`.
