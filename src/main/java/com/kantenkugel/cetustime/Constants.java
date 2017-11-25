package com.kantenkugel.cetustime;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public final class Constants {
    public static final long UPDATE_INTERVAL = 10;
    public static final TimeUnit UPDATE_INTERVAL_UNIT = TimeUnit.SECONDS;

    //forces a re-creation of new message after this amount of time... 0 to disable
    public static final long FORCED_RENEW_INTERVAL = 2;
    public static final ChronoUnit FORCED_RENEW_INTERVAL_UNIT = ChronoUnit.HOURS;
}
