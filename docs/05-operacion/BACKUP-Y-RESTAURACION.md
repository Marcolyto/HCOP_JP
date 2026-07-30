# Backup y restauración

## Base PostgreSQL

Backup:

```powershell
docker compose exec -T database pg_dump -U hcop -d hcop_jp -Fc > hcop-jp.backup
```

Restauración sobre una base vacía:

```powershell
docker compose exec -T database pg_restore -U hcop -d hcop_jp --clean --if-exists < hcop-jp.backup
```

## Archivos clínicos

El volumen `hcop_jp_storage` —incluidas las guías PDF cargadas desde
Configuración— debe respaldarse junto con PostgreSQL. La base sola
conserva metadatos, pero no los binarios.

## Consistencia

Para un backup manual simple:

1. cierre el uso clínico;
2. detenga la aplicación;
3. respalde base y almacenamiento;
4. vuelva a iniciar.

La restauración debe usar ambos elementos de la misma fecha.
