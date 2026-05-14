-- V7: Update unique_user_workshop constraint to allow multiple CANCELLED registrations
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS unique_user_workshop;

-- Create a unique index that ignores CANCELLED registrations
CREATE UNIQUE INDEX unique_user_workshop ON registrations (user_id, workshop_id) WHERE status != 'CANCELLED';
