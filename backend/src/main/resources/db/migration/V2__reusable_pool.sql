-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

CREATE TABLE partner (
 id bigint not null auto_increment primary key,
 reference varchar(80) not null,
 name varchar(120) not null,
 department_id bigint not null,
 created_by bigint not null,
 enabled boolean not null default 0,
 version bigint not null default 0,
 constraint uq_partner_reference unique(reference),
 constraint fk_partner_department_id foreign key(department_id) references department(id),
 constraint fk_partner_created_by foreign key(created_by) references account(id)
);

CREATE TABLE pool (
 id bigint not null auto_increment primary key,
 reference varchar(80) not null,
 name varchar(120) not null,
 kind varchar(60) not null,
 department_id bigint not null,
 created_by bigint not null,
 enabled boolean not null default 0,
 version bigint not null default 0,
 total bigint not null default 0,
 available bigint not null default 0,
 reserved bigint not null default 0,
 out_transit bigint not null default 0,
 held bigint not null default 0,
 return_reserved bigint not null default 0,
 return_transit bigint not null default 0,
 inspection bigint not null default 0,
 repair bigint not null default 0,
 lost bigint not null default 0,
 retired bigint not null default 0,
 constraint uq_pool_reference unique(reference),
 constraint fk_pool_department_id foreign key(department_id) references department(id),
 constraint fk_pool_created_by foreign key(created_by) references account(id)
);

CREATE TABLE partner_balance (
 id bigint not null auto_increment primary key,
 pool_id bigint not null,
 partner_id bigint not null,
 held bigint not null default 0,
 return_reserved bigint not null default 0,
 constraint uq_partner_balance unique(pool_id,partner_id),
 constraint fk_partner_balance_pool_id foreign key(pool_id) references pool(id),
 constraint fk_partner_balance_partner_id foreign key(partner_id) references partner(id)
);

CREATE TABLE movement (
 id bigint not null auto_increment primary key,
 reference varchar(80) not null,
 kind varchar(20) not null,
 status varchar(30) not null,
 pool_id bigint not null,
 partner_id bigint not null,
 department_id bigint not null,
 created_by bigint not null,
 approved_by bigint null,
 dispatched_by bigint null,
 received_by bigint null,
 resolved_by bigint null,
 qc_by bigint null,
 quantity bigint not null default 0,
 receipt_quantity bigint not null default 0,
 recovered_quantity bigint not null default 0,
 lost_quantity bigint not null default 0,
 back_to_partner bigint not null default 0,
 good_quantity bigint not null default 0,
 repair_quantity bigint not null default 0,
 retire_quantity bigint not null default 0,
 version bigint not null default 0,
 constraint uq_movement_reference unique(reference),
 constraint fk_movement_pool_id foreign key(pool_id) references pool(id),
 constraint fk_movement_partner_id foreign key(partner_id) references partner(id),
 constraint fk_movement_department_id foreign key(department_id) references department(id),
 constraint fk_movement_created_by foreign key(created_by) references account(id),
 constraint fk_movement_approved_by foreign key(approved_by) references account(id),
 constraint fk_movement_dispatched_by foreign key(dispatched_by) references account(id),
 constraint fk_movement_received_by foreign key(received_by) references account(id),
 constraint fk_movement_resolved_by foreign key(resolved_by) references account(id),
 constraint fk_movement_qc_by foreign key(qc_by) references account(id)
);

CREATE TABLE adjustment (
 id bigint not null auto_increment primary key,
 reference varchar(80) not null,
 kind varchar(20) not null,
 status varchar(30) not null,
 pool_id bigint not null,
 department_id bigint not null,
 created_by bigint not null,
 approved_by bigint null,
 quantity bigint not null default 0,
 good_quantity bigint not null default 0,
 retire_quantity bigint not null default 0,
 version bigint not null default 0,
 constraint uq_adjustment_reference unique(reference),
 constraint fk_adjustment_pool_id foreign key(pool_id) references pool(id),
 constraint fk_adjustment_department_id foreign key(department_id) references department(id),
 constraint fk_adjustment_created_by foreign key(created_by) references account(id),
 constraint fk_adjustment_approved_by foreign key(approved_by) references account(id)
);

CREATE TABLE ledger_entry (
 id bigint not null auto_increment primary key,
 pool_id bigint not null,
 partner_id bigint null,
 object_type varchar(30) not null,
 object_id bigint not null,
 reference varchar(80) not null,
 from_bucket varchar(30) not null,
 to_bucket varchar(30) not null,
 quantity bigint not null default 0,
 actor_id bigint not null,
 created_at timestamp(6) not null,
 constraint fk_ledger_entry_pool_id foreign key(pool_id) references pool(id),
 constraint fk_ledger_entry_partner_id foreign key(partner_id) references partner(id),
 constraint fk_ledger_entry_actor_id foreign key(actor_id) references account(id)
);

CREATE TABLE balance_statement (
 id bigint not null auto_increment primary key,
 reference varchar(80) not null,
 status varchar(30) not null,
 pool_id bigint not null,
 partner_id bigint not null,
 department_id bigint not null,
 created_by bigint not null,
 confirmed_by bigint null,
 held bigint not null default 0,
 cutoff bigint not null default 0,
 snapshot longtext not null,
 created_at timestamp(6) not null,
 version bigint not null default 0,
 constraint uq_balance_statement_reference unique(reference),
 constraint fk_balance_statement_pool_id foreign key(pool_id) references pool(id),
 constraint fk_balance_statement_partner_id foreign key(partner_id) references partner(id),
 constraint fk_balance_statement_department_id foreign key(department_id) references department(id),
 constraint fk_balance_statement_created_by foreign key(created_by) references account(id),
 constraint fk_balance_statement_confirmed_by foreign key(confirmed_by) references account(id)
);

ALTER TABLE account ADD COLUMN partner_id bigint NULL;
ALTER TABLE account ADD CONSTRAINT fk_account_partner FOREIGN KEY(partner_id) REFERENCES partner(id);

CREATE TABLE command_record (id bigint auto_increment PRIMARY KEY, request_key varchar(36) NOT NULL UNIQUE, fingerprint varchar(64) NOT NULL, result_id bigint NOT NULL);

CREATE TABLE business_event (id bigint auto_increment PRIMARY KEY, object_type varchar(30) NOT NULL, object_id bigint NOT NULL, actor_id bigint NOT NULL, action varchar(60) NOT NULL, note varchar(1000) NOT NULL, snapshot longtext NOT NULL, created_at timestamp(6) NOT NULL, CONSTRAINT fk_event_actor FOREIGN KEY(actor_id) REFERENCES account(id));

CREATE INDEX ix_movement_scope ON movement(department_id,partner_id,status);

CREATE INDEX ix_ledger_partner_pool ON ledger_entry(partner_id,pool_id,id);

CREATE INDEX ix_event_object ON business_event(object_type,object_id,id);
