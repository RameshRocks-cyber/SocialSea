-- MVStore
CREATE ALIAS IF NOT EXISTS READ_BLOB_MAP FOR 'org.h2.tools.Recover.readBlobMap';
CREATE ALIAS IF NOT EXISTS READ_CLOB_MAP FOR 'org.h2.tools.Recover.readClobMap';
-- LOB
CREATE TABLE IF NOT EXISTS INFORMATION_SCHEMA.LOB_BLOCKS(LOB_ID BIGINT, SEQ INT, DATA VARBINARY, PRIMARY KEY(LOB_ID, SEQ));
-- lobMap.size: 0
-- lobData.size: 0
-- Layout
-- chunk.21f = chunk:21f,block:3,len:b,pages:20,livePages:0,max:c9b0,liveMax:0,map:91,root:87c00029a694,time:78d959a6,unused:78d959ad,unusedAtVersion:220,version:21f,toc:a9b1,occupancy:ffffffff
-- chunk.220 = chunk:220,block:e,len:1,pages:1,livePages:0,max:400,liveMax:0,map:91,root:880000002b54,time:78d959a8,unused:78d959ad,unusedAtVersion:220,version:220,toc:476,occupancy:01
-- chunk.221 = chunk:221,block:f,len:b,pages:20,livePages:1f,max:c9b0,liveMax:c5b0,map:91,root:88400029a694,time:78d959ad,unusedAtVersion:221,version:221,toc:a9b1,occupancy:00000080
-- chunk.3 = chunk:3,block:2,len:1,pages:5,livePages:1,max:b20,liveMax:400,map:b,root:c000021710,time:82d,unusedAtVersion:3,version:3,toc:a04,occupancy:1b
-- meta.id = 1
-- root.1 = 8840002479c7
-- root.10 = 88400020e286
-- root.17 = 88400020f418
-- root.2 = 884000002b43
-- root.20 = 88400022c390
-- root.25 = 8840002342c6
-- root.26 = 884000235590
-- root.27 = 88400023b84e
-- root.28 = 88400024090e
-- root.2b = 8840002459c8
-- root.2c = 884000247580
-- root.5 = 88400004264b
-- Meta
-- map.10 = name:table.24,createVersion:3,key:8fa25204,val:c6a36c75
-- map.11 = name:table.28,createVersion:3,key:8fa25204,val:cba585e1
-- map.12 = name:table.32,createVersion:3,key:8fa25204,val:5803b3f1
-- map.13 = name:table.36,createVersion:3,key:8fa25204,val:5eaca19a
-- map.14 = name:table.40,createVersion:3,key:8fa25204,val:48f3eb48
-- map.15 = name:index.41,createVersion:3,key:8b8fde74,val:d5029344
-- map.16 = name:table.43,createVersion:3,key:8fa25204,val:5eaca19a
-- map.17 = name:table.47,createVersion:3,key:8fa25204,val:ae458082
-- map.18 = name:table.51,createVersion:3,key:8fa25204,val:15d5a01e
-- map.19 = name:table.55,createVersion:3,key:8fa25204,val:915e803b
-- map.1a = name:table.59,createVersion:3,key:8fa25204,val:c6a36c75
-- map.1b = name:table.63,createVersion:3,key:8fa25204,val:5803b3f1
-- map.1c = name:table.67,createVersion:3,key:8fa25204,val:f25aa7b7
-- map.1d = name:table.71,createVersion:3,key:8fa25204,val:6ad869dc
-- map.1e = name:table.75,createVersion:3,key:8fa25204,val:5eaca19a
-- map.1f = name:table.79,createVersion:3,key:8fa25204,val:5803b3f1
-- map.2 = name:_
-- map.20 = name:table.83,createVersion:3,key:8fa25204,val:a608f9a7
-- map.21 = name:table.88,createVersion:3,key:8fa25204,val:f25aa7b7
-- map.22 = name:index.92,createVersion:3,key:8b89fd9d,val:d5029344
-- map.25 = name:index.98,createVersion:3,key:8b9fe92a,val:d5029344
-- map.26 = name:index.99,createVersion:3,key:8ddf5e46,val:d5029344
-- map.27 = name:index.100,createVersion:3,key:8b8d42ba,val:d5029344
-- map.28 = name:index.101,createVersion:3,key:8b8d42ba,val:d5029344
-- map.2b = name:index.107,createVersion:3,key:8b940cc7,val:d5029344
-- map.2c = name:index.109,createVersion:3,key:8b942ad7,val:d5029344
-- map.2e = name:index.113,createVersion:3,key:8b8dc267,val:d5029344
-- map.2f = name:index.115,createVersion:3,key:8b8eaee7,val:d5029344
-- map.3 = name:openTransactions
-- map.30 = name:index.117,createVersion:3,key:8b8eb2a1,val:d5029344
-- map.31 = name:index.119,createVersion:3,key:8b8a75be,val:d5029344
-- map.32 = name:index.121,createVersion:3,key:8b8a7980,val:d5029344
-- map.33 = name:index.123,createVersion:3,key:8b89fd9d,val:d5029344
-- map.34 = name:index.125,createVersion:3,key:8b8a015f,val:d5029344
-- map.35 = name:index.127,createVersion:3,key:8b89857c,val:d5029344
-- map.36 = name:index.129,createVersion:3,key:8b89893e,val:d5029344
-- map.37 = name:index.131,createVersion:3,key:8b901b04,val:d5029344
-- map.38 = name:index.133,createVersion:3,key:8b89857c,val:d5029344
-- map.39 = name:index.135,createVersion:3,key:8b89893e,val:d5029344
-- map.3a = name:index.137,createVersion:3,key:8b8eb2a1,val:d5029344
-- map.3b = name:index.139,createVersion:3,key:8b8be1db,val:d5029344
-- map.3c = name:index.141,createVersion:3,key:8b89fd9d,val:d5029344
-- map.3d = name:index.143,createVersion:3,key:8b8a015f,val:d5029344
-- map.3e = name:index.145,createVersion:3,key:8b8cd225,val:d5029344
-- map.3f = name:index.147,createVersion:3,key:8b8a75be,val:d5029344
-- map.40 = name:index.149,createVersion:3,key:8b8a7980,val:d5029344
-- map.41 = name:index.151,createVersion:3,key:8b89857c,val:d5029344
-- map.42 = name:index.153,createVersion:3,key:8b89893e,val:d5029344
-- map.43 = name:index.155,createVersion:3,key:8b89fd9d,val:d5029344
-- map.44 = name:index.157,createVersion:3,key:8b8a015f,val:d5029344
-- map.5 = name:table.0,key:8fa25204,val:5803b3f1
-- map.6 = name:lobMap,key:8fa25204,val:f4470498
-- map.7 = name:tempLobMap,key:8fa25204,val:59a6a071
-- map.8 = name:lobRef,key:eabe0274,val:391e2a
-- map.85 = name:index.94,createVersion:152,key:8b9818c0,val:ad895ff0
-- map.86 = name:index.96,createVersion:152,key:8b989140,val:ad895ff0
-- map.87 = name:index.103,createVersion:152,key:8b989140,val:ad895ff0
-- map.88 = name:index.105,createVersion:152,key:8b997d9f,val:ad895ff0
-- map.89 = name:index.111,createVersion:152,key:8b8cbf5b,val:ad895ff0
-- map.9 = name:lobData,key:8fa25204,val:59a6a071
-- map.90 = name:undoLog.1,createVersion:1f8
-- map.91 = name:undoLog.2,createVersion:1f8
-- map.b = name:table.3,createVersion:2,key:8fa25204,val:42dc6ef9
-- map.c = name:table.8,createVersion:3,key:8fa25204,val:f25aa7b7
-- map.d = name:table.12,createVersion:3,key:8fa25204,val:5803b3f1
-- map.e = name:table.16,createVersion:3,key:8fa25204,val:915e803b
-- map.f = name:table.20,createVersion:3,key:8fa25204,val:6ad869dc
-- name._ = 2
-- name.index.100 = 27
-- name.index.101 = 28
-- name.index.103 = 87
-- name.index.105 = 88
-- name.index.107 = 2b
-- name.index.109 = 2c
-- name.index.111 = 89
-- name.index.113 = 2e
-- name.index.115 = 2f
-- name.index.117 = 30
-- name.index.119 = 31
-- name.index.121 = 32
-- name.index.123 = 33
-- name.index.125 = 34
-- name.index.127 = 35
-- name.index.129 = 36
-- name.index.131 = 37
-- name.index.133 = 38
-- name.index.135 = 39
-- name.index.137 = 3a
-- name.index.139 = 3b
-- name.index.141 = 3c
-- name.index.143 = 3d
-- name.index.145 = 3e
-- name.index.147 = 3f
-- name.index.149 = 40
-- name.index.151 = 41
-- name.index.153 = 42
-- name.index.155 = 43
-- name.index.157 = 44
-- name.index.41 = 15
-- name.index.92 = 22
-- name.index.94 = 85
-- name.index.96 = 86
-- name.index.98 = 25
-- name.index.99 = 26
-- name.lobData = 9
-- name.lobMap = 6
-- name.lobRef = 8
-- name.openTransactions = 3
-- name.table.0 = 5
-- name.table.12 = d
-- name.table.16 = e
-- name.table.20 = f
-- name.table.24 = 10
-- name.table.28 = 11
-- name.table.3 = b
-- name.table.32 = 12
-- name.table.36 = 13
-- name.table.40 = 14
-- name.table.43 = 16
-- name.table.47 = 17
-- name.table.51 = 18
-- name.table.55 = 19
-- name.table.59 = 1a
-- name.table.63 = 1b
-- name.table.67 = 1c
-- name.table.71 = 1d
-- name.table.75 = 1e
-- name.table.79 = 1f
-- name.table.8 = c
-- name.table.83 = 20
-- name.table.88 = 21
-- name.tempLobMap = 7
-- name.undoLog.1 = 90
-- name.undoLog.2 = 91
-- Types
-- 15d5a01e = org.h2.mvstore.tx.VersionedValueType@15d5a01e
-- 1e965426 = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 261099e1 = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 27ac3b6d = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 391e2a = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 42dc6ef9 = org.h2.mvstore.tx.VersionedValueType@42dc6ef9
-- 48f3eb48 = org.h2.mvstore.tx.VersionedValueType@48f3eb48
-- 5803b3f1 = org.h2.mvstore.tx.VersionedValueType@5803b3f1
-- 59a6a071 = org.h2.mvstore.type.ByteArrayDataType@59a6a071
-- 5c0f508b = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 5e853265 = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 5eaca19a = org.h2.mvstore.tx.VersionedValueType@5eaca19a
-- 6ad869dc = org.h2.mvstore.tx.VersionedValueType@6ad869dc
-- 6c49835d = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 78b2d29e = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 7d7c05fa = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 7d95a717 = org.h2.mvstore.db.NullValueDataType@2752f6e2
-- 8934dde5 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- 8b89857c = org.h2.mvstore.db.RowDataType@8b89857c
-- 8b89893e = org.h2.mvstore.db.RowDataType@8b89893e
-- 8b89fd9d = org.h2.mvstore.db.RowDataType@8b89fd9d
-- 8b8a015f = org.h2.mvstore.db.RowDataType@8b8a015f
-- 8b8a75be = org.h2.mvstore.db.RowDataType@8b8a75be
-- 8b8a7980 = org.h2.mvstore.db.RowDataType@8b8a7980
-- 8b8be1db = org.h2.mvstore.db.RowDataType@8b8be1db
-- 8b8cbf5b = org.h2.mvstore.db.RowDataType@8b8cbf5b
-- 8b8cd225 = org.h2.mvstore.db.RowDataType@8b8cd225
-- 8b8d42ba = org.h2.mvstore.db.RowDataType@8b8d42ba
-- 8b8dc267 = org.h2.mvstore.db.RowDataType@8b8dc267
-- 8b8eaee7 = org.h2.mvstore.db.RowDataType@8b8eaee7
-- 8b8eb2a1 = org.h2.mvstore.db.RowDataType@8b8eb2a1
-- 8b8fde74 = org.h2.mvstore.db.RowDataType@8b8fde74
-- 8b901b04 = org.h2.mvstore.db.RowDataType@8b901b04
-- 8b940cc7 = org.h2.mvstore.db.RowDataType@8b940cc7
-- 8b942ad7 = org.h2.mvstore.db.RowDataType@8b942ad7
-- 8b9818c0 = org.h2.mvstore.db.RowDataType@8b9818c0
-- 8b989140 = org.h2.mvstore.db.RowDataType@8b989140
-- 8b997d9f = org.h2.mvstore.db.RowDataType@8b997d9f
-- 8b9fe92a = org.h2.mvstore.db.RowDataType@8b9fe92a
-- 8bbebf0b = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- 8ddf5e46 = org.h2.mvstore.db.RowDataType@8ddf5e46
-- 8fa25204 = org.h2.mvstore.type.LongDataType@8fa25204
-- 915e803b = org.h2.mvstore.tx.VersionedValueType@915e803b
-- a608f9a7 = org.h2.mvstore.tx.VersionedValueType@a608f9a7
-- a8478894 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- a8ae2a79 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- ad895ff0 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- ae458082 = org.h2.mvstore.tx.VersionedValueType@ae458082
-- b9720e33 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- c6a36c75 = org.h2.mvstore.tx.VersionedValueType@c6a36c75
-- cba585e1 = org.h2.mvstore.tx.VersionedValueType@cba585e1
-- cbadd948 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- d5029344 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- eabe0274 = org.h2.mvstore.db.LobStorageMap$BlobReference$Type@eabe0274
-- f25aa7b7 = org.h2.mvstore.tx.VersionedValueType@f25aa7b7
-- f297b603 = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- f32b148f = org.h2.mvstore.tx.VersionedValueType@f2697b8c
-- f4470498 = org.h2.mvstore.db.LobStorageMap$BlobMeta$Type@f4470498
-- Tables
---- Schema SET ----
SET CREATE_BUILD 224;
---- Table Data ----
CREATE TABLE O_47(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR, C4 VARCHAR, C5 VARCHAR, C6 VARCHAR, C7 VARCHAR, C8 VARCHAR, C9 VARCHAR, C10 VARCHAR);
INSERT INTO O_47 VALUES(1, TIMESTAMP '2026-05-24 23:16:02.803622', '8bbfe8c9-97ea-4686-99cb-31c46c4adb65', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:16:02.803622', TIMESTAMP '2026-05-24 23:16:15.804506', 'superseded', 'ebef533a-667b-4f3b-b7f1-3ab02d2cbab5', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 1);
INSERT INTO O_47 VALUES(2, TIMESTAMP '2026-05-24 23:16:15.804506', '8bbfe8c9-97ea-4686-99cb-31c46c4adb65', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:16:15.804506', TIMESTAMP '2026-05-24 23:16:46.835815', 'superseded', '6582f47a-aa68-4352-9665-ba57ef008f5b', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 1);
INSERT INTO O_47 VALUES(3, TIMESTAMP '2026-05-24 23:16:46.835815', '8bbfe8c9-97ea-4686-99cb-31c46c4adb65', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:16:46.835815', TIMESTAMP '2026-05-25 15:08:11.456989', 'superseded', '74c3c6fa-e281-40e6-99ea-b12a45933b84', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 1);
INSERT INTO O_47 VALUES(4, TIMESTAMP '2026-05-24 23:42:52.29979', 'srv_587b7d19-0b07-4699-b9f3-a1777eb8501e', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:42:52.29979', NULL, NULL, '8cf95f5e-6a28-4e2f-88e8-b3b3f359b643', 'Mozilla/5.0 (Windows NT; Windows NT 10.0; en-IN) WindowsPowerShell/5.1.26100.8457', 2);
INSERT INTO O_47 VALUES(5, TIMESTAMP '2026-05-24 23:43:01.971024', 'srv_b9791dcc-4747-495f-838f-202b31303868', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:43:01.971024', TIMESTAMP '2026-05-24 23:44:44.5858', 'device-limit', 'cbbab9cf-c236-4edf-b5dd-9853c7cf540c', 'Mozilla/5.0 (Windows NT; Windows NT 10.0; en-IN) WindowsPowerShell/5.1.26100.8457', 3);
INSERT INTO O_47 VALUES(6, TIMESTAMP '2026-05-24 23:44:33.833231', 'srv_7cfea29d-2441-485d-93e5-7f43a7d4529c', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:44:33.833231', NULL, NULL, 'f1ad4e5e-4ead-4cbe-a17e-3a0c2e95ff73', 'Mozilla/5.0 (Windows NT; Windows NT 10.0; en-IN) WindowsPowerShell/5.1.26100.8457', 3);
INSERT INTO O_47 VALUES(7, TIMESTAMP '2026-05-24 23:44:44.5858', 'srv_06b24872-f3f9-4ae5-9786-55cb0f592139', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-24 23:44:44.5858', NULL, NULL, '2c87c553-9567-4a70-afbc-04ffeef438f7', 'Mozilla/5.0 (Windows NT; Windows NT 10.0; en-IN) WindowsPowerShell/5.1.26100.8457', 3);
INSERT INTO O_47 VALUES(8, TIMESTAMP '2026-05-25 15:08:11.456989', '8bbfe8c9-97ea-4686-99cb-31c46c4adb65', '', '0:0:0:0:0:0:0:1', TIMESTAMP '2026-05-25 22:33:48.69193', NULL, NULL, '56ae66f3-9956-49c1-844e-a9bf7e748d2d', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36', 1);
CREATE TABLE O_83(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR, C4 VARCHAR, C5 VARCHAR, C6 VARCHAR, C7 VARCHAR, C8 VARCHAR, C9 VARCHAR, C10 VARCHAR, C11 VARCHAR, C12 VARCHAR, C13 VARCHAR, C14 VARCHAR, C15 VARCHAR, C16 VARCHAR, C17 VARCHAR, C18 VARCHAR, C19 VARCHAR, C20 VARCHAR, C21 VARCHAR, C22 VARCHAR, C23 VARCHAR, C24 VARCHAR, C25 VARCHAR);
INSERT INTO O_83 VALUES(1, FALSE, FALSE, NULL, NULL, TIMESTAMP '2026-05-24 23:16:02.733173', 'rameshnanda485@gmail.com', 14.903931, 78.01709, TIMESTAMP '2026-05-25 22:33:31.572579', FALSE, 'Ramesh', 'male', '$2a$10$F6mwXvG7DKEFHbYmMmOILuXublpUhpuSEMP2nkD69gml2WCmYkwty', NULL, 'en', TIMESTAMP '2026-05-25 22:30:47.881624', FALSE, FALSE, NULL, NULL, NULL, 'USER', FALSE, NULL, NULL);
INSERT INTO O_83 VALUES(2, FALSE, FALSE, NULL, NULL, TIMESTAMP '2026-05-24 23:42:52.233268', 'codexnav1779666172@socialsea.local', NULL, NULL, NULL, FALSE, 'codexnav1779666172', 'male', '$2a$10$fK6/rpLjB/mL0zPKnhQNee7dFs8F1o1n1L4cwJ1FO1lcIh0ltTi1i', NULL, 'en', NULL, FALSE, FALSE, NULL, NULL, NULL, 'USER', FALSE, NULL, NULL);
INSERT INTO O_83 VALUES(3, FALSE, FALSE, NULL, NULL, TIMESTAMP '2026-05-24 23:43:01.903729', 'codexdbg1779666182@socialsea.local', NULL, NULL, NULL, FALSE, 'codexdbg1779666182', 'male', '$2a$10$D5XGURX.LT8Pd5wz0bCgMOGgkqcObb3P74VA8UjeQvXnxSsdnfWTu', NULL, 'en', NULL, FALSE, FALSE, NULL, NULL, NULL, 'USER', FALSE, NULL, NULL);
CREATE TABLE O_24(C0 VARCHAR, C1 VARCHAR, C2 VARCHAR, C3 VARCHAR, C4 VARCHAR, C5 VARCHAR, C6 VARCHAR, C7 VARCHAR);
INSERT INTO O_24 VALUES(1, 0, 'rameshnanda485@gmail.com', TIMESTAMP '2026-05-24 23:20:38.714522', TIMESTAMP '2026-05-24 23:15:38.714522', '698037', 1, FALSE);
---- Schema ----
CREATE USER IF NOT EXISTS "SA" SALT '511b9f05771e5b6a' HASH '81d5360a856cc396d0fcead843d731f591a7a72e7c7d610e41d412430a54b13e' ADMIN;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_E43AE9E0_98F3_42D2_85ED_01B8C9D92670" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_F337EE40_61CD_45D9_B613_B703FE23AA35" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_1F1752CE_3F6E_4BF5_A3A2_CC2B951FD366" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_DE772C4D_ACFD_4E09_8C55_D153D5B8BF22" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_DE8637C7_1D36_4C3B_8C1F_B031596BC096" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_F6F29E21_BCEE_48E0_9F5E_9B0FF4079309" START WITH 1 RESTART WITH 2 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_914EC1AE_C424_4352_99A9_F7826F90F06D" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_18527E92_0AF4_4B4D_B53A_F3D8EF6F2723" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_B6F56210_C524_47EF_AFF4_CAB55B557BEF" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_BCBDF879_2589_4EFA_AA29_A83972C96C15" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_5759F07C_9C46_4950_8C9F_3D34BEC6A7A7" START WITH 1 RESTART WITH 9 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_7597E0C1_293F_4177_A356_D4545DE0C201" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_EEBC0454_DC11_42C9_B9F5_3D03374AD4F4" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_7F377287_9A4E_4FA1_9F05_D660863DE7FC" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_6D76D020_A3CA_47A3_B7BD_0CD4B0DB62AD" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_CAD583A3_B24D_4635_BE2B_8560A8C23AE9" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_D755A1F4_EA1B_4959_94EA_B9939A309C82" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_3B15FAD1_08BB_4C62_9607_37223B9E1B11" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_8DE7AD02_D44E_49E8_BC72_453372EC903E" START WITH 1 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_3CB64939_8BD4_4555_A5E8_C78830319435" START WITH 1 RESTART WITH 4 BELONGS_TO_TABLE;
CREATE SEQUENCE "public"."SYSTEM_SEQUENCE_4A836DDB_F42C_409C_AE86_017BB5126237" START WITH 1 BELONGS_TO_TABLE;
CREATE CACHED TABLE "public"."ambulance_driver_requests"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_E43AE9E0_98F3_42D2_85ED_01B8C9D92670" NOT NULL,
    "created_at" TIMESTAMP(6) NOT NULL,
    "driver_name" CHARACTER VARYING(120),
    "note" CHARACTER VARYING,
    "phone" CHARACTER VARYING(40),
    "reject_reason" CHARACTER VARYING(500),
    "reviewed_at" TIMESTAMP(6),
    "reviewed_by" CHARACTER VARYING(255),
    "service_name" CHARACTER VARYING(140),
    "status" CHARACTER VARYING(20) NOT NULL,
    "vehicle_number" CHARACTER VARYING(80),
    "user_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."anonymous_posts"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_F337EE40_61CD_45D9_B613_B703FE23AA35" NOT NULL,
    "approved" BOOLEAN NOT NULL,
    "content_url" CHARACTER VARYING(255),
    "created_at" TIMESTAMP(6),
    "description" CHARACTER VARYING(255),
    "like_count" BIGINT NOT NULL,
    "rejected" BOOLEAN NOT NULL,
    "rejection_reason" CHARACTER VARYING(255),
    "type" CHARACTER VARYING(255),
    "view_count" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."banned_ips"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_1F1752CE_3F6E_4BF5_A3A2_CC2B951FD366" NOT NULL,
    "banned_at" TIMESTAMP(6),
    "ip_address" CHARACTER VARYING(255),
    "reason" CHARACTER VARYING(255)
);
CREATE CACHED TABLE "public"."chat_messages"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_DE772C4D_ACFD_4E09_8C55_D153D5B8BF22" NOT NULL,
    "audio_url" CHARACTER VARYING(1200),
    "client_message_id" CHARACTER VARYING(120),
    "created_at" TIMESTAMP(6) NOT NULL,
    "delivered_at" TIMESTAMP(6),
    "file_name" CHARACTER VARYING(255),
    "media_fingerprint" CHARACTER VARYING(64),
    "media_size_bytes" BIGINT,
    "media_type" CHARACTER VARYING(40),
    "media_url" CHARACTER VARYING(1200),
    "read_at" TIMESTAMP(6),
    "text" CHARACTER VARYING(2000) NOT NULL,
    "receiver_id" BIGINT NOT NULL,
    "sender_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."comment"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_DE8637C7_1D36_4C3B_8C1F_B031596BC096" NOT NULL,
    "created_at" TIMESTAMP(6),
    "text" CHARACTER VARYING(255),
    "post_id" BIGINT,
    "user_id" BIGINT
);
CREATE CACHED TABLE "public"."email_otp"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_F6F29E21_BCEE_48E0_9F5E_9B0FF4079309" NOT NULL,
    "attempts" INTEGER NOT NULL,
    "email" CHARACTER VARYING(255),
    "expires_at" TIMESTAMP(6),
    "last_sent_at" TIMESTAMP(6),
    "otp" CHARACTER VARYING(255),
    "resend_count" INTEGER NOT NULL,
    "verified" BOOLEAN NOT NULL
);
CREATE CACHED TABLE "public"."emergency_alert"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_914EC1AE_C424_4352_99A9_F7826F90F06D" NOT NULL,
    "accuracy_meters" FLOAT(53),
    "active" BOOLEAN NOT NULL,
    "back_camera_enabled" BOOLEAN NOT NULL,
    "current_latitude" FLOAT(53),
    "current_longitude" FLOAT(53),
    "duration_ms" BIGINT,
    "ended_at" TIMESTAMP(6),
    "front_camera_enabled" BOOLEAN NOT NULL,
    "last_heartbeat_at" TIMESTAMP(6),
    "last_preview_frame" CHARACTER VARYING,
    "last_preview_frame_at" CHARACTER VARYING(255),
    "latitude" FLOAT(53) NOT NULL,
    "live_audio_active" BOOLEAN NOT NULL,
    "live_video_active" BOOLEAN NOT NULL,
    "longitude" FLOAT(53) NOT NULL,
    "media_url" CHARACTER VARYING(255),
    "radius_meters" INTEGER,
    "reporter_email" CHARACTER VARYING(255) NOT NULL,
    "started_at" TIMESTAMP(6)
);
CREATE CACHED TABLE "public"."follow_request"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_18527E92_0AF4_4B4D_B53A_F3D8EF6F2723" NOT NULL,
    "status" CHARACTER VARYING(255),
    "receiver_id" BIGINT,
    "sender_id" BIGINT
);
CREATE CACHED TABLE "public"."follows"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_B6F56210_C524_47EF_AFF4_CAB55B557BEF" NOT NULL,
    "follower_id" BIGINT,
    "following_id" BIGINT
);
CREATE CACHED TABLE "public"."job_opening"(
    "id" CHARACTER VARYING(120) NOT NULL,
    "apply_url" CHARACTER VARYING(1200),
    "company_id" CHARACTER VARYING(160),
    "company_name" CHARACTER VARYING(260),
    "created_at" BIGINT,
    "description" CHARACTER VARYING(2000),
    "duration_days" INTEGER,
    "experience" CHARACTER VARYING(200),
    "expires_at" BIGINT,
    "location" CHARACTER VARYING(260),
    "owner_key" CHARACTER VARYING(120),
    "salary" CHARACTER VARYING(200),
    "status" CHARACTER VARYING(40),
    "title" CHARACTER VARYING(300),
    "track" CHARACTER VARYING(120),
    "updated_at" BIGINT,
    "owner_id" BIGINT
);
CREATE CACHED TABLE "public"."likes"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_BCBDF879_2589_4EFA_AA29_A83972C96C15" NOT NULL,
    "post_id" BIGINT,
    "user_id" BIGINT
);
CREATE CACHED TABLE "public"."login_sessions"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_5759F07C_9C46_4950_8C9F_3D34BEC6A7A7" NOT NULL,
    "created_at" TIMESTAMP(6) NOT NULL,
    "device_id" CHARACTER VARYING(128) NOT NULL,
    "device_name" CHARACTER VARYING(200),
    "ip_address" CHARACTER VARYING(64),
    "last_seen_at" TIMESTAMP(6) NOT NULL,
    "revoked_at" TIMESTAMP(6),
    "revoked_reason" CHARACTER VARYING(64),
    "session_id" CHARACTER VARYING(64) NOT NULL,
    "user_agent" CHARACTER VARYING(512),
    "user_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."notification"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_7597E0C1_293F_4177_A356_D4545DE0C201" NOT NULL,
    "created_at" TIMESTAMP(6),
    "message" CHARACTER VARYING(255),
    "is_read" BOOLEAN,
    "recipient" CHARACTER VARYING(255),
    "title" CHARACTER VARYING(255),
    "type" CHARACTER VARYING(255)
);
CREATE CACHED TABLE "public"."post"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_EEBC0454_DC11_42C9_B9F5_3D03374AD4F4" NOT NULL,
    "approved" BOOLEAN NOT NULL,
    "cover_image_url" CHARACTER VARYING,
    "created_at" TIMESTAMP(6),
    "description" CHARACTER VARYING,
    "media_fingerprint" CHARACTER VARYING(64),
    "media_size_bytes" BIGINT,
    "media_type" CHARACTER VARYING(40),
    "media_url" CHARACTER VARYING(255),
    "original_file_name" CHARACTER VARYING(255),
    "reel" BOOLEAN NOT NULL,
    "title" CHARACTER VARYING,
    "video_settings" CHARACTER VARYING,
    "user_id" BIGINT
);
CREATE CACHED TABLE "public"."reports"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_7F377287_9A4E_4FA1_9F05_D660863DE7FC" NOT NULL,
    "anonymous_post_id" BIGINT,
    "created_at" TIMESTAMP(6),
    "post_id" BIGINT,
    "reason" CHARACTER VARYING(255),
    "resolved" BOOLEAN NOT NULL,
    "type" CHARACTER VARYING(255),
    "reporter_id" BIGINT
);
CREATE CACHED TABLE "public"."saved_post"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_6D76D020_A3CA_47A3_B7BD_0CD4B0DB62AD" NOT NULL,
    "saved_at" TIMESTAMP(6),
    "post_id" BIGINT,
    "user_id" BIGINT
);
CREATE CACHED TABLE "public"."story"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_CAD583A3_B24D_4635_BE2B_8560A8C23AE9" NOT NULL,
    "caption" CHARACTER VARYING(255),
    "created_at" TIMESTAMP(6),
    "expires_at" TIMESTAMP(6),
    "media_url" CHARACTER VARYING(255),
    "privacy" CHARACTER VARYING(255),
    "story_style" CHARACTER VARYING(255),
    "story_text" CHARACTER VARYING(255),
    "story_text_style" CHARACTER VARYING,
    "user_id" BIGINT
);
CREATE CACHED TABLE "public"."story_comments"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_D755A1F4_EA1B_4959_94EA_B9939A309C82" NOT NULL,
    "created_at" TIMESTAMP(6) NOT NULL,
    "text" CHARACTER VARYING(600) NOT NULL,
    "story_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."story_likes"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_3B15FAD1_08BB_4C62_9607_37223B9E1B11" NOT NULL,
    "story_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."story_views"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_8DE7AD02_D44E_49E8_BC72_453372EC903E" NOT NULL,
    "created_at" TIMESTAMP(6) NOT NULL,
    "story_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL
);
CREATE CACHED TABLE "public"."users"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_3CB64939_8BD4_4555_A5E8_C78830319435" NOT NULL,
    "ambulance_driver_approved" BOOLEAN DEFAULT FALSE,
    "banned" BOOLEAN NOT NULL,
    "bio" CHARACTER VARYING(255),
    "cover_photo" CHARACTER VARYING(255),
    "created_at" TIMESTAMP(6),
    "email" CHARACTER VARYING(255) NOT NULL,
    "last_latitude" FLOAT(53),
    "last_longitude" FLOAT(53),
    "location_updated_at" TIMESTAMP(6),
    "long_videos_enabled" BOOLEAN DEFAULT FALSE,
    "name" CHARACTER VARYING(255),
    "notification_voice" CHARACTER VARYING(16),
    "password" CHARACTER VARYING(255),
    "phone_number" CHARACTER VARYING(20),
    "preferred_language" CHARACTER VARYING(16),
    "presence_updated_at" TIMESTAMP(6),
    "private_account" BOOLEAN DEFAULT FALSE,
    "profile_completed" BOOLEAN DEFAULT FALSE,
    "profile_pic" CHARACTER VARYING(255),
    "resume_json" CHARACTER VARYING,
    "resume_updated_at" TIMESTAMP(6),
    "role" CHARACTER VARYING(255) NOT NULL,
    "traffic_alerts_enabled" BOOLEAN DEFAULT FALSE,
    "vault_lock_json" CHARACTER VARYING,
    "vault_lock_updated_at" TIMESTAMP(6)
);
CREATE CACHED TABLE "public"."web_push_subscription"(
    "id" BIGINT GENERATED BY DEFAULT AS IDENTITY SEQUENCE "public"."SYSTEM_SEQUENCE_4A836DDB_F42C_409C_AE86_017BB5126237" NOT NULL,
    "active" BOOLEAN NOT NULL,
    "auth" CHARACTER VARYING(256) NOT NULL,
    "created_at" TIMESTAMP(6) NOT NULL,
    "endpoint" CHARACTER VARYING(2048) NOT NULL,
    "expiration_time" BIGINT,
    "p256dh" CHARACTER VARYING(512) NOT NULL,
    "recipient" CHARACTER VARYING(255) NOT NULL,
    "updated_at" TIMESTAMP(6) NOT NULL,
    "user_agent" CHARACTER VARYING(512)
);
INSERT INTO "public"."login_sessions" SELECT * FROM O_47;
INSERT INTO "public"."users" SELECT * FROM O_83;
INSERT INTO "public"."email_otp" SELECT * FROM O_24;
DROP TABLE O_83;
DROP TABLE O_24;
DROP TABLE O_47;
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk_nudfg4apu9brl3x1kvdcm8co5_INDEX_C" ON "public"."banned_ips"("ip_address" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk4faelgsm2rxl2jf3iyjy981ro_INDEX_D" ON "public"."follows"("follower_id" NULLS LAST, "following_id" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk2jovqhqo324cubdomovkex03b_INDEX_6" ON "public"."likes"("user_id" NULLS LAST, "post_id" NULLS LAST);
CREATE INDEX "public"."idx_login_sessions_user_active" ON "public"."login_sessions"("user_id" NULLS LAST, "revoked_at" NULLS LAST);
CREATE INDEX "public"."idx_login_sessions_user_device_active" ON "public"."login_sessions"("user_id" NULLS LAST, "device_id" NULLS LAST, "revoked_at" NULLS LAST);
CREATE INDEX "public"."idx_login_sessions_session_id" ON "public"."login_sessions"("session_id" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk_qf5t7odt2aeesrtlug9yrrwlt_INDEX_2" ON "public"."login_sessions"("session_id" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk6gsqv7cnk1vevgocgg9tppdo3_INDEX_C" ON "public"."story_likes"("user_id" NULLS LAST, "story_id" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."ukfsdn05j99j64fedvl2m5wehwd_INDEX_C" ON "public"."story_views"("user_id" NULLS LAST, "story_id" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk_6dotkott2kjsp8vw4d0m25fb7_INDEX_6" ON "public"."users"("email" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk_9q63snka3mdh91as4io72espi_INDEX_6" ON "public"."users"("phone_number" NULLS LAST);
CREATE UNIQUE NULLS DISTINCT INDEX "public"."uk_web_push_subscription_endpoint_INDEX_3" ON "public"."web_push_subscription"("endpoint" NULLS LAST);
CREATE INDEX "public"."fkpl780rdhxdf52kiy3v78wf09i_INDEX_C" ON "public"."ambulance_driver_requests"("user_id" NULLS LAST);
CREATE INDEX "public"."fkand7mh9iu4kt3n1tn2w9i9of0_INDEX_6" ON "public"."chat_messages"("receiver_id" NULLS LAST);
CREATE INDEX "public"."fkgiqeap8ays4lf684x7m0r2729_INDEX_6" ON "public"."chat_messages"("sender_id" NULLS LAST);
CREATE INDEX "public"."fks1slvnkuemjsq2kj4h3vhx7i1_INDEX_3" ON "public"."comment"("post_id" NULLS LAST);
CREATE INDEX "public"."fkqm52p1v3o13hy268he0wcngr5_INDEX_3" ON "public"."comment"("user_id" NULLS LAST);
CREATE INDEX "public"."fkk96qai1gkhq80uxm76qohxn13_INDEX_1" ON "public"."follow_request"("receiver_id" NULLS LAST);
CREATE INDEX "public"."fkjwk2okdxyawxya7nr54d8un3r_INDEX_1" ON "public"."follow_request"("sender_id" NULLS LAST);
CREATE INDEX "public"."fkqnkw0cwwh6572nyhvdjqlr163_INDEX_D" ON "public"."follows"("follower_id" NULLS LAST);
CREATE INDEX "public"."fkonkdkae2ngtx70jqhsh7ol6uq_INDEX_D" ON "public"."follows"("following_id" NULLS LAST);
CREATE INDEX "public"."fkt4wp5w1h0wdkmb6oj64o7j460_INDEX_9" ON "public"."job_opening"("owner_id" NULLS LAST);
CREATE INDEX "public"."fkowd6f4s7x9f3w50pvlo6x3b41_INDEX_6" ON "public"."likes"("post_id" NULLS LAST);
CREATE INDEX "public"."fknvx9seeqqyy71bij291pwiwrg_INDEX_6" ON "public"."likes"("user_id" NULLS LAST);
CREATE INDEX "public"."fk7ky67sgi7k0ayf22652f7763r_INDEX_3" ON "public"."post"("user_id" NULLS LAST);
CREATE INDEX "public"."fkd3qiw2om5d2oh5xb7fbdcq225_INDEX_4" ON "public"."reports"("reporter_id" NULLS LAST);
CREATE INDEX "public"."fken1lxuu640imywwubiaq784j2_INDEX_5" ON "public"."saved_post"("post_id" NULLS LAST);
CREATE INDEX "public"."fk34f2hx4ffhdd6mo082idwf3mw_INDEX_5" ON "public"."saved_post"("user_id" NULLS LAST);
CREATE INDEX "public"."fksw852hm0bw9owsnyjifithh6b_INDEX_6" ON "public"."story"("user_id" NULLS LAST);
CREATE INDEX "public"."fk8m4bej6r1ayop1vxsbgjo4jbu_INDEX_8" ON "public"."story_comments"("story_id" NULLS LAST);
CREATE INDEX "public"."fkieml2e4ob8i71u11r4wsn3p2h_INDEX_8" ON "public"."story_comments"("user_id" NULLS LAST);
CREATE INDEX "public"."fk59copx2suik88dwa887bwsoa4_INDEX_C" ON "public"."story_likes"("story_id" NULLS LAST);
CREATE INDEX "public"."fko2p5ura3qbwbn1lb46om8en1w_INDEX_C" ON "public"."story_likes"("user_id" NULLS LAST);
CREATE INDEX "public"."fkoarxahf8fai2nq69pypjfr9o5_INDEX_C" ON "public"."story_views"("story_id" NULLS LAST);
CREATE INDEX "public"."fkggl5535ip5aofgxjp4q2chcw4_INDEX_C" ON "public"."story_views"("user_id" NULLS LAST);
ALTER TABLE "public"."ambulance_driver_requests" ADD CONSTRAINT "public"."CONSTRAINT_C" CHECK("status" IN('PENDING', 'APPROVED', 'REJECTED')) NOCHECK;
ALTER TABLE "public"."ambulance_driver_requests" ADD CONSTRAINT "public"."CONSTRAINT_C8" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_C";
ALTER TABLE "public"."anonymous_posts" ADD CONSTRAINT "public"."CONSTRAINT_6" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_6";
ALTER TABLE "public"."banned_ips" ADD CONSTRAINT "public"."CONSTRAINT_C1" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_C1";
ALTER TABLE "public"."chat_messages" ADD CONSTRAINT "public"."CONSTRAINT_62" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_62";
ALTER TABLE "public"."comment" ADD CONSTRAINT "public"."CONSTRAINT_3" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_3";
ALTER TABLE "public"."email_otp" ADD CONSTRAINT "public"."CONSTRAINT_7" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_7";
ALTER TABLE "public"."emergency_alert" ADD CONSTRAINT "public"."CONSTRAINT_F" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_F";
ALTER TABLE "public"."follow_request" ADD CONSTRAINT "public"."CONSTRAINT_1" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_1";
ALTER TABLE "public"."follows" ADD CONSTRAINT "public"."CONSTRAINT_D" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_D";
ALTER TABLE "public"."job_opening" ADD CONSTRAINT "public"."CONSTRAINT_9" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_9";
ALTER TABLE "public"."likes" ADD CONSTRAINT "public"."CONSTRAINT_623" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_623";
ALTER TABLE "public"."login_sessions" ADD CONSTRAINT "public"."CONSTRAINT_2" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_2";
ALTER TABLE "public"."notification" ADD CONSTRAINT "public"."CONSTRAINT_23" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_23";
ALTER TABLE "public"."post" ADD CONSTRAINT "public"."CONSTRAINT_34" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_34";
ALTER TABLE "public"."reports" ADD CONSTRAINT "public"."CONSTRAINT_4" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_4";
ALTER TABLE "public"."saved_post" ADD CONSTRAINT "public"."CONSTRAINT_5" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_5";
ALTER TABLE "public"."story" ADD CONSTRAINT "public"."CONSTRAINT_68" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_68";
ALTER TABLE "public"."story_comments" ADD CONSTRAINT "public"."CONSTRAINT_8" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_8";
ALTER TABLE "public"."story_likes" ADD CONSTRAINT "public"."CONSTRAINT_C9" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_C9";
ALTER TABLE "public"."story_views" ADD CONSTRAINT "public"."CONSTRAINT_C99" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_C99";
ALTER TABLE "public"."users" ADD CONSTRAINT "public"."CONSTRAINT_6A" CHECK("role" IN('USER', 'MODERATOR', 'ADMIN', 'SUPER_ADMIN')) NOCHECK;
ALTER TABLE "public"."users" ADD CONSTRAINT "public"."CONSTRAINT_6A6" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_6A";
ALTER TABLE "public"."web_push_subscription" ADD CONSTRAINT "public"."CONSTRAINT_3E" PRIMARY KEY("id") INDEX "public"."PRIMARY_KEY_3E";
ALTER TABLE "public"."banned_ips" ADD CONSTRAINT "public"."uk_nudfg4apu9brl3x1kvdcm8co5" UNIQUE("ip_address") INDEX "public"."uk_nudfg4apu9brl3x1kvdcm8co5_INDEX_C";
ALTER TABLE "public"."follows" ADD CONSTRAINT "public"."uk4faelgsm2rxl2jf3iyjy981ro" UNIQUE("follower_id", "following_id") INDEX "public"."uk4faelgsm2rxl2jf3iyjy981ro_INDEX_D";
ALTER TABLE "public"."likes" ADD CONSTRAINT "public"."uk2jovqhqo324cubdomovkex03b" UNIQUE("user_id", "post_id") INDEX "public"."uk2jovqhqo324cubdomovkex03b_INDEX_6";
ALTER TABLE "public"."login_sessions" ADD CONSTRAINT "public"."uk_qf5t7odt2aeesrtlug9yrrwlt" UNIQUE("session_id") INDEX "public"."uk_qf5t7odt2aeesrtlug9yrrwlt_INDEX_2";
ALTER TABLE "public"."story_likes" ADD CONSTRAINT "public"."uk6gsqv7cnk1vevgocgg9tppdo3" UNIQUE("user_id", "story_id") INDEX "public"."uk6gsqv7cnk1vevgocgg9tppdo3_INDEX_C";
ALTER TABLE "public"."story_views" ADD CONSTRAINT "public"."ukfsdn05j99j64fedvl2m5wehwd" UNIQUE("user_id", "story_id") INDEX "public"."ukfsdn05j99j64fedvl2m5wehwd_INDEX_C";
ALTER TABLE "public"."users" ADD CONSTRAINT "public"."uk_6dotkott2kjsp8vw4d0m25fb7" UNIQUE("email") INDEX "public"."uk_6dotkott2kjsp8vw4d0m25fb7_INDEX_6";
ALTER TABLE "public"."users" ADD CONSTRAINT "public"."uk_9q63snka3mdh91as4io72espi" UNIQUE("phone_number") INDEX "public"."uk_9q63snka3mdh91as4io72espi_INDEX_6";
ALTER TABLE "public"."web_push_subscription" ADD CONSTRAINT "public"."uk_web_push_subscription_endpoint" UNIQUE("endpoint") INDEX "public"."uk_web_push_subscription_endpoint_INDEX_3";
ALTER TABLE "public"."ambulance_driver_requests" ADD CONSTRAINT "public"."fkpl780rdhxdf52kiy3v78wf09i" FOREIGN KEY("user_id") INDEX "public"."fkpl780rdhxdf52kiy3v78wf09i_INDEX_C" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."chat_messages" ADD CONSTRAINT "public"."fkand7mh9iu4kt3n1tn2w9i9of0" FOREIGN KEY("receiver_id") INDEX "public"."fkand7mh9iu4kt3n1tn2w9i9of0_INDEX_6" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."chat_messages" ADD CONSTRAINT "public"."fkgiqeap8ays4lf684x7m0r2729" FOREIGN KEY("sender_id") INDEX "public"."fkgiqeap8ays4lf684x7m0r2729_INDEX_6" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."comment" ADD CONSTRAINT "public"."fks1slvnkuemjsq2kj4h3vhx7i1" FOREIGN KEY("post_id") INDEX "public"."fks1slvnkuemjsq2kj4h3vhx7i1_INDEX_3" REFERENCES "public"."post"("id") NOCHECK;
ALTER TABLE "public"."comment" ADD CONSTRAINT "public"."fkqm52p1v3o13hy268he0wcngr5" FOREIGN KEY("user_id") INDEX "public"."fkqm52p1v3o13hy268he0wcngr5_INDEX_3" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."follow_request" ADD CONSTRAINT "public"."fkk96qai1gkhq80uxm76qohxn13" FOREIGN KEY("receiver_id") INDEX "public"."fkk96qai1gkhq80uxm76qohxn13_INDEX_1" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."follow_request" ADD CONSTRAINT "public"."fkjwk2okdxyawxya7nr54d8un3r" FOREIGN KEY("sender_id") INDEX "public"."fkjwk2okdxyawxya7nr54d8un3r_INDEX_1" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."follows" ADD CONSTRAINT "public"."fkqnkw0cwwh6572nyhvdjqlr163" FOREIGN KEY("follower_id") INDEX "public"."fkqnkw0cwwh6572nyhvdjqlr163_INDEX_D" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."follows" ADD CONSTRAINT "public"."fkonkdkae2ngtx70jqhsh7ol6uq" FOREIGN KEY("following_id") INDEX "public"."fkonkdkae2ngtx70jqhsh7ol6uq_INDEX_D" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."job_opening" ADD CONSTRAINT "public"."fkt4wp5w1h0wdkmb6oj64o7j460" FOREIGN KEY("owner_id") INDEX "public"."fkt4wp5w1h0wdkmb6oj64o7j460_INDEX_9" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."likes" ADD CONSTRAINT "public"."fkowd6f4s7x9f3w50pvlo6x3b41" FOREIGN KEY("post_id") INDEX "public"."fkowd6f4s7x9f3w50pvlo6x3b41_INDEX_6" REFERENCES "public"."post"("id") NOCHECK;
ALTER TABLE "public"."likes" ADD CONSTRAINT "public"."fknvx9seeqqyy71bij291pwiwrg" FOREIGN KEY("user_id") INDEX "public"."fknvx9seeqqyy71bij291pwiwrg_INDEX_6" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."post" ADD CONSTRAINT "public"."fk7ky67sgi7k0ayf22652f7763r" FOREIGN KEY("user_id") INDEX "public"."fk7ky67sgi7k0ayf22652f7763r_INDEX_3" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."reports" ADD CONSTRAINT "public"."fkd3qiw2om5d2oh5xb7fbdcq225" FOREIGN KEY("reporter_id") INDEX "public"."fkd3qiw2om5d2oh5xb7fbdcq225_INDEX_4" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."saved_post" ADD CONSTRAINT "public"."fken1lxuu640imywwubiaq784j2" FOREIGN KEY("post_id") INDEX "public"."fken1lxuu640imywwubiaq784j2_INDEX_5" REFERENCES "public"."post"("id") NOCHECK;
ALTER TABLE "public"."saved_post" ADD CONSTRAINT "public"."fk34f2hx4ffhdd6mo082idwf3mw" FOREIGN KEY("user_id") INDEX "public"."fk34f2hx4ffhdd6mo082idwf3mw_INDEX_5" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."story" ADD CONSTRAINT "public"."fksw852hm0bw9owsnyjifithh6b" FOREIGN KEY("user_id") INDEX "public"."fksw852hm0bw9owsnyjifithh6b_INDEX_6" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."story_comments" ADD CONSTRAINT "public"."fk8m4bej6r1ayop1vxsbgjo4jbu" FOREIGN KEY("story_id") INDEX "public"."fk8m4bej6r1ayop1vxsbgjo4jbu_INDEX_8" REFERENCES "public"."story"("id") NOCHECK;
ALTER TABLE "public"."story_comments" ADD CONSTRAINT "public"."fkieml2e4ob8i71u11r4wsn3p2h" FOREIGN KEY("user_id") INDEX "public"."fkieml2e4ob8i71u11r4wsn3p2h_INDEX_8" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."story_likes" ADD CONSTRAINT "public"."fk59copx2suik88dwa887bwsoa4" FOREIGN KEY("story_id") INDEX "public"."fk59copx2suik88dwa887bwsoa4_INDEX_C" REFERENCES "public"."story"("id") NOCHECK;
ALTER TABLE "public"."story_likes" ADD CONSTRAINT "public"."fko2p5ura3qbwbn1lb46om8en1w" FOREIGN KEY("user_id") INDEX "public"."fko2p5ura3qbwbn1lb46om8en1w_INDEX_C" REFERENCES "public"."users"("id") NOCHECK;
ALTER TABLE "public"."story_views" ADD CONSTRAINT "public"."fkoarxahf8fai2nq69pypjfr9o5" FOREIGN KEY("story_id") INDEX "public"."fkoarxahf8fai2nq69pypjfr9o5_INDEX_C" REFERENCES "public"."story"("id") NOCHECK;
ALTER TABLE "public"."story_views" ADD CONSTRAINT "public"."fkggl5535ip5aofgxjp4q2chcw4" FOREIGN KEY("user_id") INDEX "public"."fkggl5535ip5aofgxjp4q2chcw4_INDEX_C" REFERENCES "public"."users"("id") NOCHECK;
DROP ALIAS READ_BLOB_MAP;
DROP ALIAS READ_CLOB_MAP;
DROP TABLE IF EXISTS INFORMATION_SCHEMA.LOB_BLOCKS;
