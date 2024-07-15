Vertex plugin
=============

Kill Bill tax plugin for [Vertex](https://www.vertexinc.com/) using [Vertex Indirect Tax O Series REST APIs v2](https://tax-calc-api.vertexcloud.com/).

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

* `org.killbill.billing.plugin.vertex.url`: Vertex api base url
* `org.killbill.billing.plugin.vertex.clientId`: Vertex client id
* `org.killbill.billing.plugin.vertex.clientSecret`: Vertex client secret

The following properties are optional:

* `org.killbill.billing.plugin.vertex.companyName`: company name
* `org.killbill.billing.plugin.vertex.companyDivision`: company division
* `org.killbill.billing.plugin.vertex.adjustments.lenientMode`: when true, Vertex-plugin will skip adjustment items if previousInvoiceId is missing. Otherwise, an IllegalStateException is thrown, and invoice generation is aborted
* `org.killbill.billing.plugin.vertex.skipInvoiceTaxRate`: when true, the plugin will skip invoice level tax rate calculation and won't store it in "invoiceTaxRate" custom field for the invoice

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
org.killbill.billing.plugin.vertex.clientId=YYY
org.killbill.billing.plugin.vertex.clientSecret=ZZZ' \
     http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/killbill-vertex
```
