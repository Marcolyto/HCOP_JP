ALTER TABLE application_preparation_lots
  ADD COLUMN component_key varchar(255);

WITH legacy_candidates AS (
  SELECT lot.id,
         reservation.component_key,
         row_number() OVER (
           PARTITION BY lot.patient_id, lot.treatment_id, lot.cycle_number,
                        lot.application_day, reservation.component_key,
                        lot.preparation_status
           ORDER BY lot.created_at, lot.id
         ) AS occurrence
    FROM application_preparation_lots lot
    JOIN application_stock_reservations reservation
      ON reservation.id = lot.stock_reservation_id
   WHERE lot.component_key IS NULL
)
UPDATE application_preparation_lots lot
   SET component_key = candidate.component_key
  FROM legacy_candidates candidate
 WHERE lot.id = candidate.id
   AND candidate.occurrence = 1;

CREATE UNIQUE INDEX uq_application_preparation_active_component
  ON application_preparation_lots
    (patient_id, treatment_id, cycle_number, application_day, component_key)
  WHERE preparation_status = 'active' AND component_key IS NOT NULL;

COMMENT ON COLUMN application_preparation_lots.component_key IS
  'Clave canónica del componente prescripto. Puede ser NULL únicamente en trazas legacy no resolubles.';
