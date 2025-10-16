-- 1. 验证总数（应该是1024条）
SELECT COUNT(*) FROM d_sharding_route_mapping;

-- 2. 验证每个物理分片的虚拟分片数量（每个应该是128条）
SELECT physical_database_suffix, physical_table_suffix, COUNT(*) AS cnt
FROM d_sharding_route_mapping
GROUP BY physical_database_suffix, physical_table_suffix
ORDER BY physical_database_suffix, physical_table_suffix;

-- 3. 验证虚拟分片ID的连续性（应该从0到1023）
SELECT MIN(logical_shard_id) AS min_id, MAX(logical_shard_id) AS max_id
FROM d_sharding_route_mapping;

-- 4. 查询特定虚拟分片ID（例如532）
SELECT * FROM d_sharding_route_mapping WHERE logical_shard_id = 532;
-- 应该返回：logical_shard_id=532, physical_database_suffix='1', physical_table_suffix=0