package com.tricrotism.utils;

public class TimeUtils {

    public static String getTimeAmount(final long timeInMilliseconds, boolean isShort, boolean decimals) {
        final long timeInSeconds = Math.round(timeInMilliseconds / 1000.0F);

        final long days, minutes, hours, seconds;
        days = timeInSeconds / 86400;
        hours = timeInSeconds % 86400 / 3600;
        minutes = timeInSeconds % 86400 % 3600 / 60;
        seconds = timeInSeconds % 86400 % 3600 % 60;

        final boolean daysZero, hoursZero, minutesZero, secondsZero;
        daysZero = days == 0;
        hoursZero = hours == 0;
        minutesZero = minutes == 0;
        secondsZero = seconds == 0;

        final StringBuilder amount = new StringBuilder();

        if (!daysZero) {
            amount.append(days);

            if (!isShort) {
                amount.append(days == 1 ? " day" : " days");
            } else {
                amount.append("d ");
            }
        }
        if (!hoursZero) {
            if (!daysZero && !isShort) {
                amount.append(minutesZero && secondsZero ? " and " : ", ");
            }
            amount.append(hours);

            if (!isShort) {
                amount.append(hours == 1 ? " hour" : " hours");
            } else {
                amount.append("h ");
            }
        }
        if (!minutesZero) {
            if ((!daysZero || !hoursZero) && !isShort) {
                amount.append(secondsZero ? " and " : ", ");
            }
            amount.append(minutes);

            if (!isShort) {
                amount.append(minutes == 1 ? " minute" : " minutes");
            } else {
                amount.append("m ");
            }
        }
        if (!secondsZero) {
            if (!isShort) {
                if (!daysZero) {
                    amount.append(hoursZero ? " and " : ", and ");
                } else if (!hoursZero || !minutesZero) {
                    amount.append(" and ");
                }
            }

            if (decimals) {
                amount.append(NumberUtils.format(timeInMilliseconds / 1000.0D)).append(isShort ? "s" : " seconds");
            } else if (!isShort) {
                amount.append(seconds);
                amount.append(seconds == 1 ? " second" : " seconds");
            } else {
                amount.append(seconds);
                amount.append("s");
            }
        } else if (timeInMilliseconds < 1000) {
            amount.append(NumberUtils.format(timeInMilliseconds / 1000.0D)).append(isShort ? "s" : " seconds");
        }
        return amount.toString().trim();
    }

}
