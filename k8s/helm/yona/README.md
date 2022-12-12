# Yona Server Helm Chart

Yona server components

* https://github.com/yonadev/yona-server

The Yona Server backs the Yona mobile app, providing user management, buddy relationship management, web traffic classification
and buddy messaging.

## Chart Details

This chart will do the following:

* 2 x Yona Admin, Analysis, App, and Batch services
* All using Kubernetes Deployments

## Chart dependencies

Pull down the dependant charts (ldap, mariadb, hazelcast)

helm dependency update

## Installing the Chart

To install the chart with the release name `my-release`:

```bash
$ helm install --name my-release stable/yona-server
```

## Configuration

The following tables lists the configurable parameters of the Yona Server chart and their default values.

### Yona General

| Parameter       | Description                | Default |
|-----------------|----------------------------|---------|
| `Relase.Number` | Yona Server release number | Not set 

Specify each parameter using the `--set key=value[,key=value]` argument to `helm install`.

Alternatively, a YAML file that specifies the values for the parameters can be provided while installing the chart. For example,

```bash
$ helm install --name my-release -f values.yaml stable/yona-server
```

> **Tip**: You can use the default [values.yaml](values.yaml)

## Persistence

The yona-server components currently have no persistence requirements

## Custom ConfigMap

When creating a new chart with this chart as a dependency, CustomConfigMap can be used to override the default config.xml
provided.
It also allows for providing additional xml configuration files that will be copied into `/app`. In the parent chart's
values.yaml,
set the value to true and provide the file `templates/config.yaml` for your use case. If you start by copying `config.yaml` from
this chart and
want to access values from this chart you must change all references from `.Values` to `.Values.jenkins`.

```
yona-server:
  general:
    CustomConfigMap: true
```
