package org.strategoxt.imp.runtime.services.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.strategoxt.imp.runtime.EditorState;
import org.strategoxt.imp.runtime.services.menus.contribs.IBuilder;
import org.strategoxt.imp.runtime.services.menus.contribs.IMenuContribution;
import org.strategoxt.imp.runtime.services.menus.contribs.Menu;

/**
 * @author Oskar van Rest
 */
public class ToolbarBaseCommandHandler implements IHandler, IElementUpdater {

	private ArrayList<List<String>> lastActions = new ArrayList<>(MenusServiceConstants.NO_OF_TOOLBAR_MENUS);

	public void setLastAction(int menuIndex, List<String> pathOfLastAction) {
		lastActions.set(menuIndex, pathOfLastAction);
	}

	@Override
	public void addHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		int menuIndex = Integer.parseInt((String) event.getParameter(MenusServiceConstants.MENU_ID_PARAM));
		MenuList menus = MenusServiceUtil.getMenus();

		IBuilder builder = null;

		List<String> lastAction = lastActions.get(menuIndex);
		if (lastAction != null) {
			builder = menus.getBuilder(lastAction);
		}

		if (builder == null) {
			builder = getSomeAction(menus.getAll().get(menuIndex));
		}

		if (builder != null) {
			builder.scheduleExecute(EditorState.getActiveEditor(), null, null, false);
		}

		return null;
	}

	private IBuilder getSomeAction(Menu menu) {
		for (IMenuContribution contrib : menu.getMenuContributions()) {
			switch (contrib.getContributionType()) {
			case IMenuContribution.BUILDER:
				return (IBuilder) contrib;

			case IMenuContribution.MENU:
				IBuilder action = getSomeAction((Menu) contrib);
				if (action != null) {
					return action;
				} else {
					break;
				}
			default:
				break;
			}
		}

		return null;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener handlerListener) {
	}

	@Override
	public void updateElement(UIElement element, @SuppressWarnings("rawtypes") Map parameters) {
		int menuIndex = Integer.parseInt((String) parameters.get(MenusServiceConstants.MENU_ID_PARAM));
		MenuList menus = MenusServiceUtil.getMenus();
		
		if (menus.getAll().size() > menuIndex) {
			String caption = menus.getAll().get(menuIndex).getCaption();
			
			element.setText(caption);
			element.setTooltip("");

			Display.getDefault().asyncExec(new Runnable() {
				@Override
				public void run() {
					MenusServiceUtil.refreshToolbarMenus();
				}
			});
		}
	}
}
