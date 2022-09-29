package com.pixelmk.pixelmkmenu.controls;

import javax.annotation.Nullable;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;

public class ActionInstance implements OnPress {

	public Button source;
	protected ButtonAction action;
	protected Object data;

	public ActionInstance(ButtonAction action, Object data) {
		this.action = action;
		this.data = data;
	}

	@Override
	public void onPress(Button button) {
		this.action.onPress(this);
	}

	@Nullable
	public Object getData() {
		return this.data;
	}

}
