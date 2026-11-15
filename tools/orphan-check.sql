SELECT 'fault_event' AS tbl, COUNT(*) AS c FROM fault_event WHERE project_id=10;
SELECT 'propagation_analysis', COUNT(*) FROM propagation_analysis WHERE project_id=10;
SELECT 'recovery_plan', COUNT(*) FROM recovery_plan WHERE project_id=10;
SELECT 'asm_check_run', COUNT(*) FROM asm_check_run WHERE project_id=10;
SELECT 'redundancy_group', COUNT(*) FROM redundancy_group WHERE project_id=10;SELECT 'redundancy_member_orphan' AS tbl, COUNT(*) FROM redundancy_member rm WHERE NOT EXISTS (SELECT 1 FROM asm_node n WHERE n.id = rm.node_id);
