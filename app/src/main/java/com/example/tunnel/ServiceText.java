package com.example.tunnel;

import android.content.Context;

final class ServiceText {
    static String translate(Context context, String value) {
        if (value == null) return null;
        Context localized = AppLocale.context(context);
        if (value.startsWith("VPN подключён: ")) return localized.getString(R.string.journal_connected_prefix) + value.substring("VPN подключён: ".length());
        if ("VPN-сервер недоступен; запущено переподключение".equals(value)) return localized.getString(R.string.journal_lost_reconnect);
        if ("VPN-сервер недоступен; отключение".equals(value)) return localized.getString(R.string.journal_lost_disconnect);
        if (value.startsWith("Подписка обновлена: ")) {
            String rest = value.substring("Подписка обновлена: ".length());
            int separator = rest.lastIndexOf(" · серверов: ");
            if (separator >= 0) rest = rest.substring(0,separator) + localized.getString(R.string.journal_servers_count) + rest.substring(separator + " · серверов: ".length());
            return localized.getString(R.string.journal_updated_prefix) + rest;
        }
        switch (value) {
            case "Подготовка…": return localized.getString(R.string.service_text_0);
            case "Завершение попытки подключения…": return localized.getString(R.string.service_text_1);
            case "Ожидание интернета…": return localized.getString(R.string.service_text_2);
            case "Ожидание интернета": return localized.getString(R.string.service_waiting_internet);
            case "VPN занят": return localized.getString(R.string.service_vpn_busy);
            case "Выберите серверы": return localized.getString(R.string.service_select_servers);
            case "Выберите сервер в списке": return localized.getString(R.string.service_select_server);
            case "Не удалось запустить VPN": return localized.getString(R.string.service_start_failed);
            case "Проверка серверов…": return localized.getString(R.string.service_text_3);
            case "Обновление движков…": return localized.getString(R.string.service_text_4);
            case "Обновление подписок…": return localized.getString(R.string.service_text_5);
            case "Повторная проверка серверов…": return localized.getString(R.string.service_text_6);
            case "Повторное подключение…": return localized.getString(R.string.service_text_7);
            case "Ожидание проверки интернета…": return localized.getString(R.string.service_text_8);
            case "Обновление подписки…": return localized.getString(R.string.service_text_9);
            case "Проверка движков…": return localized.getString(R.string.service_text_10);
            case "Подключение…": return localized.getString(R.string.service_text_11);
            case "Переподключение…": return localized.getString(R.string.service_text_12);
            case "Подключено": return localized.getString(R.string.service_text_13);
            case "Не подключено": return localized.getString(R.string.service_text_14);
            case "Соединение потеряно": return localized.getString(R.string.service_text_15);
            case "Не удалось подключиться": return localized.getString(R.string.service_text_16);
            case "Проверка": return localized.getString(R.string.service_text_17);
            case "Ожидание": return localized.getString(R.string.service_text_18);
            case "Не поддерживается": return localized.getString(R.string.service_text_19);
            case "Движок недоступен": return localized.getString(R.string.service_text_20);
            case "Начало подключения VPN": return localized.getString(R.string.service_text_21);
            case "Отключение VPN": return localized.getString(R.string.service_text_22);
            case "Интернет недоступен; ожидание сети": return localized.getString(R.string.service_text_23);
            case "Интернет восстановлен": return localized.getString(R.string.service_text_24);
            case "Попытка подключения не удалась": return localized.getString(R.string.service_text_25);
            case "Автоматическое подключение VPN": return localized.getString(R.string.service_text_26);
            case "Начато восстановление VPN": return localized.getString(R.string.service_text_27);
            case "Время проверки сервера истекло": return localized.getString(R.string.service_text_28);
            case "Время подключения к серверу истекло": return localized.getString(R.string.service_text_29);
            case "Достигнут предел кругов восстановления": return localized.getString(R.string.service_text_30);
            case "Требуется разрешение VPN": return localized.getString(R.string.service_text_31);
            case "Достигнут предел попыток": return localized.getString(R.string.service_text_32);
            case "Сервер недоступен": return localized.getString(R.string.service_text_33);
            case "Нет выбранных установленных приложений": return localized.getString(R.string.service_text_34);
            case "Движок AWG недоступен": return localized.getString(R.string.service_text_35);
            case "Движок Xray недоступен": return localized.getString(R.string.service_text_36);
            case "Не удалось подключиться: доступные серверы закончились": return localized.getString(R.string.service_text_37);
        }
        if (value.startsWith("Проверка · ")) return localized.getString(R.string.service_check_server) + value.substring("Проверка · ".length());
        if (value.matches("[0-9]+ мс")) return value.substring(0,value.length()-3) + localized.getString(R.string.service_milliseconds);
        if (value.startsWith("Подключение: ")) return localized.getString(R.string.service_prefix_0) + value.substring(13);
        if (value.startsWith("Проверка ")) {
            String detail=value.substring("Проверка ".length());
            if(detail.endsWith(" мс"))detail=detail.substring(0,detail.length()-3)+localized.getString(R.string.service_milliseconds);
            return localized.getString(R.string.service_prefix_1)+detail;
        }
        if (value.startsWith("Обновление подписки: ")) return localized.getString(R.string.service_prefix_2) + value.substring(21);
        if (value.startsWith("Получено обновление подписки: ")) {
            String detail=value.substring("Получено обновление подписки: ".length());
            int separator=detail.lastIndexOf(" · серверов: ");
            if(separator>=0)detail=detail.substring(0,separator)+localized.getString(R.string.journal_servers_count)+detail.substring(separator+" · серверов: ".length());
            return localized.getString(R.string.service_prefix_3)+detail;
        }
        if (value.startsWith("Не удалось обновить подписку: ")) return localized.getString(R.string.service_prefix_4) + value.substring(30);
        if (value.startsWith("Не удалось подключиться: ")) return localized.getString(R.string.service_prefix_5) + value.substring(25);
        if (value.startsWith("Ручное подключение: ")) return localized.getString(R.string.service_prefix_6) + value.substring(20);
        if (value.startsWith("Ошибка обновления подписки: ")) return localized.getString(R.string.service_prefix_7) + value.substring(28);
        if (value.startsWith("Ошибка подготовки VPN: ")) return localized.getString(R.string.service_prefix_8) + value.substring(23);
        if (value.startsWith("Завершён круг восстановления: ")) return localized.getString(R.string.service_prefix_9) + value.substring(30);
        return value;
    }
}
