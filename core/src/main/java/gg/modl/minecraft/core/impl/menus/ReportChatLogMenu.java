package gg.modl.minecraft.core.impl.menus;

import dev.simplix.cirrus.actionhandler.ActionHandlers;
import dev.simplix.cirrus.item.CirrusItem;
import dev.simplix.cirrus.item.CirrusItemType;
import dev.simplix.cirrus.menu.CirrusInventoryType;
import dev.simplix.cirrus.menus.SimpleMenu;
import dev.simplix.cirrus.model.CallResult;
import dev.simplix.cirrus.model.Click;
import dev.simplix.cirrus.text.CirrusChatElement;
import gg.modl.minecraft.api.AbstractPlayer;
import gg.modl.minecraft.api.http.ModlHttpClient;
import gg.modl.minecraft.core.Platform;
import gg.modl.minecraft.core.config.ReportGuiConfig;
import gg.modl.minecraft.core.impl.menus.util.MenuItems;
import gg.modl.minecraft.core.impl.menus.util.ReportRenderUtil;
import gg.modl.minecraft.core.locale.LocaleManager;
import gg.modl.minecraft.core.service.ChatMessageCache;
import java.util.Map;
import static gg.modl.minecraft.core.util.Java8Collections.listOf;
import static gg.modl.minecraft.core.util.Java8Collections.mapOf;

public class ReportChatLogMenu extends SimpleMenu {

    private final AbstractPlayer reporter, target;
    private final ModlHttpClient httpClient;
    private final LocaleManager locale;
    private final Platform platform;
    private final String panelUrl;
    private final ReportGuiConfig guiConfig;
    private final ChatMessageCache chatMessageCache;
    private final ReportData reportData;

    public ReportChatLogMenu(AbstractPlayer reporter, AbstractPlayer target, ModlHttpClient httpClient,
                              LocaleManager locale, Platform platform, String panelUrl,
                              ReportGuiConfig guiConfig, ChatMessageCache chatMessageCache,
                              ReportData reportData) {
        super();
        this.reporter = reporter;
        this.target = target;
        this.httpClient = httpClient;
        this.locale = locale;
        this.platform = platform;
        this.panelUrl = panelUrl;
        this.guiConfig = guiConfig;
        this.chatMessageCache = chatMessageCache;
        this.reportData = reportData;

        title(locale.getMessage("messages.report_gui_title", mapOf("player", target.getUsername())));
        type(CirrusInventoryType.GENERIC_9X3);
        buildMenu();
    }

    private void buildMenu() {
        set(CirrusItem.of(
                CirrusItemType.of("minecraft:orange_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_attach_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_attach_chat_lore"))
        ).slot(11).actionHandler("attachChat"));

        set(buildTargetHead("messages.report_skull_chat_log").slot(13));

        set(CirrusItem.of(
                CirrusItemType.of("minecraft:light_blue_terracotta"),
                CirrusChatElement.ofLegacyText(locale.getMessage("messages.report_skip_chat")),
                MenuItems.lore(locale.getMessageList("messages.report_skip_chat_lore"))
        ).slot(15).actionHandler("skipChat"));

        set(MenuItems.backButton().slot(22));
    }

    private CirrusItem buildTargetHead(String localeKey) {
        return ReportRenderUtil.buildTargetHead(locale, platform, target, localeKey);
    }

    private void openDetailsMenu(Click click) {
        ActionHandlers.openMenu(new ReportDetailsMenu(
                reporter, target, httpClient, locale, platform, panelUrl,
                guiConfig, chatMessageCache, reportData, this
        )).handle(click);
    }

    @Override
    protected void registerActionHandlers() {
        registerActionHandler("attachChat", click -> {
            String chatLog = chatMessageCache.getChatLogForReport(target.getUuid().toString());

            if (chatLog.isEmpty()) {
                platform.sendMessage(reporter.getUuid(),
                        locale.getMessage("messages.no_chat_logs_available", mapOf("player", target.getUsername())));
            } else {
                reportData.setChatLog(chatLog);
            }

            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("skipChat", click -> {
            openDetailsMenu(click);
            return CallResult.DENY_GRABBING;
        });

        registerActionHandler("back", click -> {
            ActionHandlers.openMenu(new ReportMenu(
                    reporter, target, httpClient, locale, platform, panelUrl,
                    guiConfig, chatMessageCache
            )).handle(click);
            return CallResult.DENY_GRABBING;
        });
    }
}
