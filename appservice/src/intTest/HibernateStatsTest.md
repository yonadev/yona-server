# HibernateStatsTest

| Operation| Close statement| Collection fetch| Collection load| Collection recreate| Collection remove| Collection update| Connect| Entity delete| Entity fetch| Entity insert| Entity load| Entity update| Flush| Optimistic failure| Prepare statement| Query cache hit| Query cache miss| Query cache put| Query execution| Query execution max time| Second level cache hit| Second level cache miss| Second level cache put| Session close| Session open| Successful transaction| Transaction
| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---| ---
| GetUserWithoutPrivateDataFirst| 0| 0| 0| 0| 0| 0| 1| 0| 0| 0| 1| 0| 1| 0| 1| 0| 0| 0| 0| 0| 0| 0| 0| 3| 3| 1| 1
| GetUserWithoutPrivateDataSecond| 0| 0| 0| 0| 0| 0| 1| 0| 0| 0| 1| 0| 1| 0| 1| 0| 0| 0| 0| 0| 0| 0| 0| 2| 2| 1| 1
| GetUserWithPrivateDataFirst| 0| 4| 8| 0| 0| 0| 1| 0| 23| 0| 57| 0| 3| 0| 31| 0| 0| 0| 3| 0| 0| 0| 0| 2| 2| 3| 3
| GetUserWithPrivateDataSecond| 0| 1| 5| 0| 0| 0| 1| 0| 2| 0| 33| 0| 3| 0| 7| 0| 0| 0| 3| 0| 0| 0| 0| 2| 2| 3| 3
| GetMessagesFirst| 0| 1| 5| 0| 0| 0| 1| 0| 8| 0| 106| 0| 4| 0| 33| 0| 0| 0| 13| 36| 0| 0| 0| 2| 2| 6| 6
| GetMessagesSecond| 0| 1| 5| 0| 0| 0| 1| 0| 8| 0| 106| 0| 4| 0| 33| 0| 0| 0| 13| 28| 0| 0| 0| 2| 2| 6| 6
| GetDayActivityOverviewsFirst| 0| 3| 6| 0| 0| 0| 1| 0| 2| 0| 29| 0| 2| 0| 8| 0| 0| 0| 1| 0| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityOverviewsSecond| 0| 1| 5| 0| 0| 0| 1| 0| 3| 0| 26| 0| 2| 0| 6| 0| 0| 0| 1| 1| 0| 0| 0| 2| 2| 2| 2
| GetWeekActivityOverviewsFirst| 0| 5| 17| 0| 0| 0| 1| 0| 2| 0| 68| 0| 2| 0| 10| 0| 0| 0| 1| 0| 0| 0| 0| 2| 2| 2| 2
| GetWeekActivityOverviewsSecond| 0| 3| 15| 0| 0| 0| 1| 0| 3| 0| 64| 0| 2| 0| 8| 0| 0| 0| 1| 1| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityOverviewsWithBuddiesFirst| 0| 6| 46| 0| 0| 0| 1| 0| 3| 0| 230| 0| 3| 0| 20| 0| 0| 0| 10| 0| 0| 0| 0| 2| 2| 3| 3
| GetDayActivityOverviewsWithBuddiesSecond| 0| 5| 45| 0| 0| 0| 1| 0| 2| 0| 229| 0| 3| 0| 18| 0| 0| 0| 10| 0| 0| 0| 0| 2| 2| 3| 3
| GetDayActivityDetailsLastFridayFirst| 0| 3| 4| 0| 0| 0| 1| 0| 2| 0| 22| 0| 2| 0| 8| 0| 0| 0| 1| 0| 0| 0| 0| 2| 2| 2| 2
| GetDayActivityDetailsLastFridaySecond| 0| 1| 2| 0| 0| 0| 1| 0| 2| 0| 16| 0| 2| 0| 5| 0| 0| 0| 1| 0| 0| 0| 0| 2| 2| 2| 2
