package com.singlehandedmode;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

@Singleton
public class FakeDialogueManager
{
    private final Client client;
    private final ClientThread clientThread;

    // Track if our fake dialogue is open so we can close it properly
    private boolean isDialogueOpen = false;
    private Runnable onContinueCallback = null;

    @Inject
    public FakeDialogueManager(Client client, ClientThread clientThread)
    {
        this.client = client;
        this.clientThread = clientThread;
    }

    public void openNpcDialogue(int npcId, String title, String text, Runnable onContinue)
    {
        clientThread.invokeLater(() ->
        {
            // 1. Get the Chatbox Container
            Widget chatbox = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
            if (chatbox == null || chatbox.isHidden()) return;

            // 2. Open the Chatbox input layer (Clears the chat log temporarily)
            // Script 175: OpenChatBoxWidget
            client.runScript(175, 1, 1, 0, 0, 0, -1, "");

            // 3. Clear any existing children to be safe
            chatbox.deleteAllChildren();

            // 4. Create the UI Structure
            // A. The NPC Head Model
            Widget head = chatbox.createChild(-1, WidgetType.MODEL);
            head.setModelId(npcId); // NPC ID works for Chatheads usually
            head.setModelType(WidgetModelType.NPC_CHATHEAD);
            head.setAnimationId(567); // Standard "Talking" animation
            head.setOriginalX(4);
            head.setOriginalY(4);
            head.setOriginalWidth(32);
            head.setOriginalHeight(32);
            head.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            head.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            head.setWidthMode(WidgetSizeMode.ABSOLUTE);
            head.setHeightMode(WidgetSizeMode.ABSOLUTE);
            head.setRotationX(40); // Standard Angle
            head.setRotationZ(1880); // Standard Rotation
            head.setModelZoom(796); // Zoom in on face
            head.revalidate();

            // B. The Title (NPC Name)
            Widget nameWidget = chatbox.createChild(-1, WidgetType.TEXT);
            nameWidget.setText(title);
            nameWidget.setFontId(FontID.QUILL_8);
            nameWidget.setTextColor(0x800000); // Dark Red/Brown
            nameWidget.setOriginalX(44);
            nameWidget.setOriginalY(6);
            nameWidget.setOriginalWidth(400);
            nameWidget.setOriginalHeight(12);
            nameWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            nameWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            nameWidget.setTextShadowed(true);
            nameWidget.revalidate();

            // C. The Body Text
            Widget textWidget = chatbox.createChild(-1, WidgetType.TEXT);
            textWidget.setText(text);
            textWidget.setFontId(FontID.QUILL_8);
            textWidget.setTextColor(0x000000); // Black
            textWidget.setOriginalX(44);
            textWidget.setOriginalY(22);
            textWidget.setOriginalWidth(380);
            textWidget.setOriginalHeight(60);
            textWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            textWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            textWidget.setTextShadowed(false);
            textWidget.revalidate();

            // D. "Click here to continue"
            Widget continueWidget = chatbox.createChild(-1, WidgetType.TEXT);
            continueWidget.setText("Click here to continue");
            continueWidget.setFontId(FontID.QUILL_8);
            continueWidget.setTextColor(0x0000FF); // Blue
            continueWidget.setOriginalX(44);
            continueWidget.setOriginalY(85);
            continueWidget.setOriginalWidth(400);
            continueWidget.setOriginalHeight(12);
            continueWidget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            continueWidget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            continueWidget.setTextShadowed(true);

            // 5. INTERACTION (The Click Listener)
            // We put a big invisible box over the whole chatbox to catch the click
            Widget clickCatcher = chatbox.createChild(-1, WidgetType.RECTANGLE);
            clickCatcher.setOriginalWidth(519);
            clickCatcher.setOriginalHeight(165);
            clickCatcher.setFilled(true);
            clickCatcher.setOpacity(255); // Invisible
            clickCatcher.setHasListener(true);

            // This is the Magic: Client-side event listener
            clickCatcher.setOnOpListener((JavaScriptCallback) ev ->
            {
                closeDialogue();
                if (onContinue != null) onContinue.run();
            });

            clickCatcher.revalidate();

            isDialogueOpen = true;
            this.onContinueCallback = onContinue;
        });
    }

    public void closeDialogue()
    {
        if (!isDialogueOpen) return;

        clientThread.invokeLater(() ->
        {
            // Reset the chatbox to normal
            // Script: RestoreChatBox
            client.runScript(ScriptID.CHAT_PROMPT_INIT, 0, "");

            Widget chatbox = client.getWidget(WidgetInfo.CHATBOX_CONTAINER);
            if (chatbox != null)
            {
                chatbox.deleteAllChildren();
                chatbox.revalidate();
            }
            isDialogueOpen = false;
        });
    }
}