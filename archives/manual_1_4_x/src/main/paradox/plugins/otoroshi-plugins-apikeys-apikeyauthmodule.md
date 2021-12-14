
@@@ div { .plugin .plugin-hidden .plugin-kind-preroute }

# Apikey auth module

<img class="plugin-logo plugin-hidden" src=""></img>

## Infos

* plugin type: `preroute`
* configuration root: `ApikeyAuthModule`

## Description

This plugin adds basic auth on service where credentials are valid apikeys on the current service.



## Default configuration

```json
{
  "ApikeyAuthModule" : {
    "realm" : "apikey-auth-module-realm",
    "noneTagIn" : [ ],
    "oneTagIn" : [ ],
    "allTagsIn" : [ ],
    "noneMetaIn" : [ ],
    "oneMetaIn" : [ ],
    "allMetaIn" : [ ],
    "noneMetaKeysIn" : [ ],
    "oneMetaKeyIn" : [ ],
    "allMetaKeysIn" : [ ]
  }
}
```





@@@

