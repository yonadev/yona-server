# Yona Smoothwall Log Parser

This program will watch the output of the DansGuardian access logs for the json data with the categorization.  It will cross reference this against a list of categories to watch (via API request) and will forward the payloads onto the Analysis server.

See https://wiki.yona.nu/display/DEV/Perl+script+to+pass+events+from+SmoothWall+to+the+Yona+server

### Install on Smoothwall Server

Create a tarball with 
```
make
```
in this directory, or download from

```
http://repo.ops.yona.nu/yona_smoothwall_parser_latest.tar.gz
```

Unpack under / on the smoothwall server.   Will expand into 
```
modules
└── yona
    ├── bin
    │   ├── HandleDansGuardianLog.pl
    │   └── yona-log-parser
    ├── etc
    │   └── actions
    │       └── secondboot
    │           └── 9399yona-log-parser
    └── settings
        ├── oem
        └── yona-log-parser.conf
```

Edit /modules/yona/settings/yona-log-parser.conf and adjust to suit



