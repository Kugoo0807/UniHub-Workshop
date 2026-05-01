-- Add unique constraint to prevent duplicate registrations
ALTER TABLE registrations
ADD CONSTRAINT uq_registrations_user_workshop UNIQUE (user_id, workshop_id);
