update messages
set buddy_message_id                = null,
    origin_goal_conflict_message_id = null,
    thread_head_message_id          = null,
    replied_message_id              = null;
delete from messages;
delete from message_sources;
delete from activities;
delete from interval_activities where dtype = 'DayActivity';
delete from interval_activities;
update goals set previous_instance_of_this_goal_id = null;
delete from goals;
delete from buddies;
delete from buddies_anonymized;
delete from users_anonymized;
delete from users;
delete from users_private;
delete from confirmation_codes;
delete from message_destinations;
delete from new_device_requests;

