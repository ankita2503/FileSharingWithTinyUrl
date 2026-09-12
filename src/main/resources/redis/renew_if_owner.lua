-- KEYS[1] = lease key, ARGV[1] = instance id, ARGV[2] = ttl seconds
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('EXPIRE', KEYS[1], ARGV[2])
end
return 0