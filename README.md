Vertex plugin
=============

Kill Bill tax plugin for [Vertex](https://www.vertexinc.com/).

This integration delegates computation of sales taxes to Vertex, which will appear directly on Kill Bill invoices.

Release builds are available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.kill-bill.billing.plugin.java%22%20AND%20a%3A%22vertex-plugin%22) with coordinates `org.kill-bill.billing.plugin.java:vertex-plugin`.

Kill Bill compatibility
-----------------------

| Plugin version | Kill Bill version |
| -------------: | ----------------: |
| 0.1.y          | 0.22.z            |


Configuration
-------------

The following properties are required:

* `org.killbill.billing.plugin.vertex.url`: Vertex endpoint

The following properties are optional:

* `org.killbill.billing.plugin.vertex.proxyHost`: proxy host
* `org.killbill.billing.plugin.vertex.proxyPort`: proxy port
* `org.killbill.billing.plugin.vertex.strictSSL`: if false, unverified certificates are trusted
* `org.killbill.billing.plugin.vertex.connectTimeout`
* `org.killbill.billing.plugin.vertex.readTimeout`

These properties can be specified globally via System Properties or on a per tenant basis:

```
curl -v \
     -X POST \
     -u admin:password \
     -H 'X-Killbill-ApiKey: bob' \
     -H 'X-Killbill-ApiSecret: lazar' \
     -H 'X-Killbill-CreatedBy: admin' \
     -H 'Content-Type: text/plain' \
     -d 'org.killbill.billing.plugin.vertex.url=XXX
org.killbill.billing.plugin.vertex.accountNumber=YYY
org.killbill.billing.plugin.vertex.licenseKey=ZZZ' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-vertex
```
