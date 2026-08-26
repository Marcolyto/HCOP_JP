CREATE INDEX idx_unified_infusion_chair_window
  ON unified_infusion_sessions (chair, scheduled_at)
  WHERE scheduled_at IS NOT NULL AND clinical_status <> 'cancelled';

CREATE OR REPLACE FUNCTION prevent_infusion_overlap()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.scheduled_at IS NULL
     OR NULLIF(btrim(NEW.chair), '') IS NULL
     OR NEW.clinical_status = 'cancelled' THEN
    RETURN NEW;
  END IF;

  -- Serializa las reservas del mismo sillón y evita la carrera
  -- "ambos verifican libre y ambos insertan".
  PERFORM pg_advisory_xact_lock(hashtextextended(lower(btrim(NEW.chair)), 24072026));

  IF EXISTS (
    SELECT 1
      FROM unified_infusion_sessions occupied
     WHERE occupied.id <> COALESCE(NEW.id, 0)
       AND occupied.clinical_status <> 'cancelled'
       AND occupied.scheduled_at IS NOT NULL
       AND lower(btrim(occupied.chair)) = lower(btrim(NEW.chair))
       AND NEW.scheduled_at <
           occupied.scheduled_at + make_interval(mins => COALESCE(occupied.duration_minutes, 1))
       AND occupied.scheduled_at <
           NEW.scheduled_at + make_interval(mins => COALESCE(NEW.duration_minutes, 1))
  ) THEN
    RAISE EXCEPTION USING
      ERRCODE = '23P01',
      MESSAGE = 'El sillón ya está ocupado en ese horario.';
  END IF;

  RETURN NEW;
END;
$$;

CREATE TRIGGER trg_prevent_infusion_overlap
BEFORE INSERT OR UPDATE OF chair, scheduled_at, duration_minutes, clinical_status
ON unified_infusion_sessions
FOR EACH ROW
EXECUTE FUNCTION prevent_infusion_overlap();

