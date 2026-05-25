\c :mosipdbname

GRANT CONNECT
   ON DATABASE mosip_digitalcard
   TO :dbuname;

GRANT USAGE
   ON SCHEMA digitalcard
   TO :dbuname;

GRANT SELECT,INSERT,UPDATE,DELETE,REFERENCES
   ON ALL TABLES IN SCHEMA digitalcard
   TO :dbuname;

