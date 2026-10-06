 # Enterprise Trading Platform

An enterprise-grade trading platform that allows customers to manage trading accounts, place buy and sell orders for financial instruments, and track their cash balances and positions.

Orders are submitted through a REST API and executed asynchronously using market prices. The platform ensures reliable and consistent trade execution, prevents duplicate order processing, validates market data freshness, and securely handles authentication.

Executed trades are persisted in a transactional database, published as events for downstream processing, and transformed into an analytical data store for reporting and insights such as risk vs return for instruments, daily volume per instrument and maximum drawdown per instrument.

The platform also provides a browser-based interface through which users can interact with their accounts, orders, trades, and positions.


## Sprint 10 extensions

Four modules built as packages inside the Trade REST API (`services/trade-api`), with Angular screens for each:
customer preferences, customer notifications, watchlists and price alerts, and portfolio and P&L.

* [Trade REST API README](services/trade-api/README.md): run, configure, test, runbook
* [Scope and backlog](docs/sprint-10/scope-and-backlog.md), [the two seams](docs/sprint-10/seams.md), [demonstration script](docs/sprint-10/demo.md)
* [Decision log](decision-log/), [combined security review](security-review/Sprint10_Security_Review.md)
* Contracts: [preferences](contracts/preferences-api.yaml), [notifications](contracts/notifications-api.yaml), [alerts](contracts/alerts-api.yaml), [portfolio](contracts/portfolio-api.yaml)
