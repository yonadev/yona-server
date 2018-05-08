# HibernateStatsTest

| Operation| Close statement| Collection fetch| Collection load| Collection recreate| Collection remove| Collection update| Connect| Entity delete| Entity fetch| Entity insert| Entity load| Entity update| Flush| Optimistic failure| Prepare statement| Query cache hit| Query cache miss| Query cache put| Query execution| Query execution max time| Second level cache hit| Second level cache miss| Second level cache put| Session close| Session open| Successful transaction| Transaction
| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---
| GetUserWithoutPrivateDataFirst| 0| 0| 0| 0| 0| 0| 1| 0| 0| 0| 1| 0| 1| 0| 1| 0| 0| 0| 0| 0| 0| 0| 0| 3| 3| 1| 1
| GetUserWithoutPrivateDataSecond| 0| 0| 0| 0| 0| 0| 1| 0| 0| 0| 1| 0| 1| 0| 1| 0| 0| 0| 0| 0| 0| 0| 0| 2| 2| 1| 1
| GetUserWithPrivateDataFirst| 0| 10| 15| 0| 0| 0| 1| 0| 23| 0| 65| 0| 3| 0| 38| 0| 0| 0| 3| 16| 0| 0| 0| 2| 2| 3| 3
| GetUserWithPrivateDataSecond| 0| 4| 9| 0| 0| 0| 1| 0| 20| 0| 59| 0| 3| 0| 32| 0| 0| 0| 3| 6| 0| 0| 0| 2| 2| 3| 3
| GetMessagesFirst| 0| 4| 9| 0| 0| 0| 1| 0| 25| 0| 128| 0| 4| 0| 46| 0| 0| 0| 10| 45| 0| 0| 0| 2| 2| 6| 6
| GetMessagesSecond| 0| 4| 9| 0| 0| 0| 1| 0| 25| 0| 128| 0| 4| 0| 46| 0| 0| 0| 10| 38| 0| 0| 0| 2| 2| 6| 6
| GetDayActivityOverviewsFirst| 0| 4| 8| 0| 0| 0| 1| 0| 2| 0| 31| 0| 2| 0| 9| 0| 0| 0| 1| 2| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityOverviewsSecond| 0| 1| 6| 0| 0| 0| 1| 0| 3| 0| 28| 0| 2| 0| 6| 0| 0| 0| 1| 3| 0| 0| 0| 2| 2| 2| 2
| GetWeekActivityOverviewsFirst| 0| 6| 19| 0| 0| 0| 1| 0| 2| 0| 70| 0| 2| 0| 11| 0| 0| 0| 1| 8| 0| 0| 0| 2| 2| 2| 2
| GetWeekActivityOverviewsSecond| 0| 3| 16| 0| 0| 0| 1| 0| 3| 0| 66| 0| 2| 0| 8| 0| 0| 0| 1| 3| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityOverviewsWithBuddiesFirst| 0| 10| 51| 0| 0| 0| 1| 0| 21| 0| 256| 0| 3| 0| 46| 0| 0| 0| 10| 6| 0| 0| 0| 2| 2| 3| 3
| GetDayActivityOverviewsWithBuddiesSecond| 0| 8| 49| 0| 0| 0| 1| 0| 20| 0| 255| 0| 3| 0| 43| 0| 0| 0| 10| 16| 0| 0| 0| 2| 2| 3| 3
| GetDayActivityDetailsLastFridayFirst| 0| 4| 6| 0| 0| 0| 1| 0| 2| 0| 24| 0| 2| 0| 9| 0| 0| 0| 1| 5| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityDetailsLastFridaySecond| 0| 1| 3| 0| 0| 0| 1| 0| 2| 0| 17| 0| 2| 0| 5| 0| 0| 0| 1| 6| 0| 0| 0| 2| 2| 2| 2
