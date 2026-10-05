# TSUKAT server — working ResourcePackManager setup

This folder is a backup of the known-working TSUKAT resource-pack delivery setup.

## Confirmed working setup

- Paper: 26.2
- ResourcePackManager: 2.4.6
- TsukatPets: 1.0.8
- Resource-pack HTTP port: **22082/TCP**
- UDP 22082 is **not required**
- Public resource-pack URL: `http://185.219.84.162:22082/rspm.zip`
- ResourcePackManager remote MagmaGuy hosting is disabled.
- Self-hosting is forced with `selfHostForce: true`.
- TsukatPets pack has top merge priority.
- FreeMinecraftModels is immediately below TsukatPets in merge priority.

## Server paths

```text
plugins/TsukatPets-1.0.8.jar
plugins/ResourcePackManager/config.yml
plugins/ResourcePackManager/mixer/TsukatPets-ResourcePack-26.2-v1.0.8.zip
```

## Important

After changes, do a full server restart. Do not rely on Bukkit/Paper `/reload`.

Useful checks:

```text
/rspm status
/tpet status
/tpet models
```

The browser test for resource-pack delivery is:

```text
http://185.219.84.162:22082/rspm.zip
```

If that URL downloads the ZIP, the self-host delivery path is reachable.

## Why this backup exists

The previous setup used MagmaGuy automatic hosting and the client logged repeated `FAILED_DOWNLOAD`. Switching ResourcePackManager to forced self-hosting on TCP 22082 fixed delivery.
