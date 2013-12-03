package io.trigger.forge.android.modules.tabs;

import io.trigger.forge.android.core.ForgeActivity;
import io.trigger.forge.android.core.ForgeApp;
import io.trigger.forge.android.core.ForgeFile;
import io.trigger.forge.android.core.ForgeJSBridge;
import io.trigger.forge.android.core.ForgeLog;
import io.trigger.forge.android.core.ForgeTask;
import io.trigger.forge.android.core.ForgeUtil;
import io.trigger.forge.android.util.BitmapUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class ModalView {
	// Reference to the last created modal view (for back button, etc)
	static ModalView lastModal = null;
	WebView webView = null;
	View view = null;
	ForgeTask task = null;

	public ModalView() {
		lastModal = this;
	}
	
	public void stringByEvaluatingJavaScriptFromString(final ForgeTask task, final String script) {
		Object wvObj = webView;
		
		try {
			Field f = wvObj.getClass().getDeclaredField("mProvider");
			f.setAccessible(true);
			wvObj = f.get(wvObj);
		} catch (NoSuchFieldException e) {
		} catch (IllegalArgumentException e) {
			return;
		} catch (IllegalAccessException e) {
			return;
		}
		
		try {
			Field f = wvObj.getClass().getDeclaredField("mWebViewCore");
			f.setAccessible(true);
			wvObj = f.get(wvObj);
			
			Field eventHubField = wvObj.getClass().getDeclaredField("mEventHub");
			eventHubField.setAccessible(true);
			Object eventHub = eventHubField.get(wvObj);
			@SuppressWarnings("rawtypes")
			Class eventHubClass = eventHub.getClass();
			
			Field handlerField = eventHubClass.getDeclaredField("mHandler");
			handlerField.setAccessible(true);
			Handler handler = (Handler) handlerField.get(eventHub);
			
			Field frameField = wvObj.getClass().getDeclaredField("mBrowserFrame");
			frameField.setAccessible(true);
			final Object browserFrame = frameField.get(wvObj);
			
			final Method callJS = browserFrame.getClass().getMethod("stringByEvaluatingJavaScriptFromString", String.class);	
			
			handler.post(new Runnable() {
				@Override
				public void run() {
					try {
						String result = (String)callJS.invoke(browserFrame, script);
						if (result == null) {
							task.success();
						} else {
							task.success(result);
						}
					} catch (IllegalArgumentException e) {
					} catch (IllegalAccessException e) {
					} catch (InvocationTargetException e) {
					}
				}
			});
		} catch (NoSuchFieldException e) {
			return;
		} catch (IllegalArgumentException e) {
			return;
		} catch (IllegalAccessException e) {
			return;
		} catch (NoSuchMethodException e) {
			return;
		} catch (NullPointerException e) {
			return;
		}
	}

	public void closeModal(final ForgeActivity currentActivity, final String url, boolean cancelled) {
		if (view == null) {
			return;
		}

		final JsonObject result = new JsonObject();
		result.addProperty("url", url);
		result.addProperty("userCancelled", cancelled);

		currentActivity.removeModalView(view, new Runnable() {
			public void run() {
				ForgeApp.event("tabs."+task.callid+".closed", result);
			}
		});

		if (lastModal == this) {
			lastModal = null;
		}

		view = null;
	}

	public void openModal(final ForgeTask task) {
		this.task = task;
		task.performUI(new Runnable() {
			public void run() {
				ForgeLog.i("Displaying modal view.");

				// Get settings
				String url = null;
				String pattern = null;
				String title = null;
				String buttonText = null;
				JsonElement buttonIcon = null;
				JsonArray buttonTint = null;
				JsonArray tint = null;
				url = task.params.get("url").getAsString();
				if (task.params.has("pattern")) {
					pattern = task.params.get("pattern").getAsString();
				}
				if (task.params.has("title")) {
					title = task.params.get("title").getAsString();
				}
				if (task.params.has("buttonText")) {
					buttonText = task.params.get("buttonText").getAsString();
				}
				if (task.params.has("buttonIcon")) {
					buttonIcon = task.params.get("buttonIcon");
				}
				if (task.params.has("tint")) {
					tint = task.params.getAsJsonArray("tint");
				}
				if (task.params.has("buttonTint")) {
					buttonTint = task.params.getAsJsonArray("buttonTint");
				}

				// Create webview
				final WebView subView = new WebView(ForgeApp.getActivity());
				// Save static reference
				webView = subView;

				// Create new layout
				LinearLayout layout = new LinearLayout(ForgeApp.getActivity());
				view = layout;
				layout.setOrientation(LinearLayout.VERTICAL);
				layout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
				layout.setBackgroundColor(0xFF000000);

				// Add a progress bar
				final ProgressBar progress = new ProgressBar(ForgeApp.getActivity(), null, android.R.attr.progressBarStyleHorizontal);
				progress.setMax(100);
				progress.setProgress(0);
				progress.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
				progress.setBackgroundColor(0xFF000000);
				layout.addView(progress);

				// Add a top bar
				LinearLayout topbar = new LinearLayout(ForgeApp.getActivity());
				topbar.setOrientation(LinearLayout.HORIZONTAL);

				int size = 50;
				DisplayMetrics metrics = new DisplayMetrics();
				ForgeApp.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
				int requiredSize = Math.round(metrics.density * size);
				final int margin = Math.round(metrics.density * 8);

				topbar.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, requiredSize));
				topbar.setGravity(Gravity.CENTER);

				int[] colors = new int[] { 0xFF000000, 0xFF000000, 0xFF000000, 0xDD000000, 0xBB000000 };
				if (tint != null) {
					int color;
					color = Color.argb(tint.get(3).getAsInt(), tint.get(0).getAsInt(), tint.get(1).getAsInt(), tint.get(2).getAsInt());
					colors = new int[] { color - 0x00000000, color - 0x00000000, color - 0x00000000, color - 0x22000000, color - 0x44000000 };
				}
				GradientDrawable bgGrad = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors);
				topbar.setBackgroundDrawable(bgGrad);

				// Add default title
				TextView titleView = new TextView(ForgeApp.getActivity());
				if (title != null) {
					titleView.setText(title);
				}
				titleView.setTextColor(0xFFFFFFFF);
				titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.density * 24);
				titleView.setGravity(Gravity.CENTER);
				topbar.addView(titleView);
				topbar.setPadding(margin, 0, margin, 0);

				// Add padding
				topbar.addView(new View(ForgeApp.getActivity()), 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
				topbar.addView(new View(ForgeApp.getActivity()), 2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));

				// Add a button
				size = 32;
				final int buttonMargin = Math.round(metrics.density * 6);
				requiredSize = Math.round(metrics.density * size);

				LinearLayout button = new LinearLayout(ForgeApp.getActivity());
				button.setLongClickable(true);

				final JsonArray fButtonTint = buttonTint != null ? buttonTint : tint;

				button.setOnTouchListener(new OnTouchListener() {
					public boolean onTouch(View v, MotionEvent event) {
						if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
							// Highlight
							int[] colors = new int[] { 0xFF000000, 0xFF000000, 0xFF000000, 0xFF222222, 0xFF444444 };
							GradientDrawable highlight = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors);
							highlight.setStroke(1, 0xAA000000);
							highlight.setCornerRadius(buttonMargin);
							v.setBackgroundDrawable(highlight);
						} else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
							// Unhighlight
							int[] colors = new int[] { 0xFF222222, 0xFF222222, 0xFF222222, 0xFF444444, 0xFF666666 };
							if (fButtonTint != null) {
								JsonArray colorArray = fButtonTint;
								int color = Color.argb(colorArray.get(3).getAsInt(), colorArray.get(0).getAsInt(), colorArray.get(1).getAsInt(), colorArray.get(2).getAsInt());
								int color2 = Color.argb(colorArray.get(3).getAsInt(), Math.min(255, colorArray.get(0).getAsInt() + 34), Math.min(255, colorArray.get(1).getAsInt() + 34), Math.min(255, colorArray.get(2).getAsInt() + 34));
								int color3 = Color.argb(colorArray.get(3).getAsInt(), Math.min(255, colorArray.get(0).getAsInt() + 68), Math.min(255, colorArray.get(1).getAsInt() + 68), Math.min(255, colorArray.get(2).getAsInt() + 68));
								colors = new int[] { color, color, color, color2, color3 };
							}
							
							GradientDrawable highlight = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors);
							highlight.setStroke(1, 0xAA000000);
							highlight.setCornerRadius(buttonMargin);
							v.setBackgroundDrawable(highlight);

							// Send event
							ForgeLog.i("Modal view close button pressed, returning to main webview.");

							closeModal(ForgeApp.getActivity(), subView.getUrl(), true);
						}
						return false;
					}
				});

				button.setOrientation(LinearLayout.VERTICAL);
				button.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, requiredSize));
				button.setGravity(Gravity.CENTER);

				if (buttonIcon != null) {
					ImageView image = new ImageView(ForgeApp.getActivity());
					image.setScaleType(ImageView.ScaleType.CENTER);
					Drawable icon;
					try {
						icon = BitmapUtil.scaledDrawableFromStream(ForgeApp.getActivity(), new ForgeFile(ForgeApp.getActivity(), buttonIcon).fd().createInputStream(), 0, 32);
					} catch (IOException e) {
						task.error(e);
						return;
					}
					image.setImageDrawable(icon);
					image.setPadding(buttonMargin, 0, buttonMargin, 0);
					button.addView(image);
				} else {
					TextView text = new TextView(ForgeApp.getActivity());
					if (buttonText != null) {
						text.setText(buttonText);
					} else {
						text.setText("Close");
					}
					text.setTextColor(0xFFCCCCCC);
					text.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.density * 14);
					text.setGravity(Gravity.CENTER);
					text.setPadding(buttonMargin * 3, buttonMargin, buttonMargin * 3, buttonMargin);
					button.addView(text);
				}

				colors = new int[] { 0xFF222222, 0xFF222222, 0xFF222222, 0xFF444444, 0xFF666666 };
				if (tint != null) {
					JsonArray colorArray = fButtonTint;
					int color = Color.argb(colorArray.get(3).getAsInt(), colorArray.get(0).getAsInt(), colorArray.get(1).getAsInt(), colorArray.get(2).getAsInt());
					int color2 = Color.argb(colorArray.get(3).getAsInt(), Math.min(255, colorArray.get(0).getAsInt() + 34), Math.min(255, colorArray.get(1).getAsInt() + 34), Math.min(255, colorArray.get(2).getAsInt() + 34));
					int color3 = Color.argb(colorArray.get(3).getAsInt(), Math.min(255, colorArray.get(0).getAsInt() + 68), Math.min(255, colorArray.get(1).getAsInt() + 68), Math.min(255, colorArray.get(2).getAsInt() + 68));
					colors = new int[] { color, color, color, color2, color3 };
				}
				GradientDrawable highlight = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors);
				highlight.setStroke(1, 0x88000000);
				highlight.setCornerRadius(buttonMargin);
				button.setBackgroundDrawable(highlight);

				topbar.addView(button, 0);

				layout.addView(topbar);

				layout.addView(subView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));

				WebSettings webSettings = subView.getSettings();
				webSettings.setJavaScriptEnabled(true);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
					webSettings.setDomStorageEnabled(true);
				}
				webSettings.setGeolocationEnabled(true);

				// Make webview behave more like Android browser
				webSettings.setBuiltInZoomControls(true);
				webSettings.setUseWideViewPort(true);

				final String fPattern = pattern;

				subView.setWebChromeClient(new WebChromeClient() {
					@Override
					public void onProgressChanged(WebView view, int newProgress) {
						progress.setProgress(newProgress);
						if (newProgress == 100) {
							progress.setVisibility(View.INVISIBLE);
						} else {
							progress.setVisibility(View.VISIBLE);
						}
						super.onProgressChanged(view, newProgress);
					}
				});
				subView.setWebViewClient(new WebViewClient() {
					@Override
					public void onPageStarted(WebView view, String url, Bitmap favicon) {
						super.onPageStarted(view, url, favicon);
						final JsonObject result = new JsonObject();
						result.addProperty("url", url);

						ForgeApp.event("tabs."+task.callid+".loadStarted", result);
					}
					@Override
					public void onPageFinished(WebView view, String url) {
						super.onPageFinished(view, url);
						
						final JsonObject result = new JsonObject();
						result.addProperty("url", url);

						ForgeApp.event("tabs."+task.callid+".loadFinished", result);
					}
					@Override
					public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
						ForgeLog.w("[Forge modal WebView error] " + description);
						
						final JsonObject result = new JsonObject();
						result.addProperty("url", failingUrl);
						result.addProperty("description", description);

						ForgeApp.event("tabs."+task.callid+".loadError", result);
					}

					@Override
					public boolean shouldOverrideUrlLoading(WebView view, String url) {
						ForgeLog.i("subView load " + url);

						checkMatchPattern(url);

						if (url.startsWith("forge:///")) {
							ForgeLog.i("forge:/// URL loaded in modal view, closing and redirecting main webview.");

							closeModal(ForgeApp.getActivity(), null, false);
							ForgeApp.getActivity().gotoUrl("content://" + ForgeApp.getActivity().getApplicationContext().getPackageName() + "/src" + url.substring(9));
							return true;
						} else if (url.startsWith("about:")) {
							// Ignore about:* URLs
							return true;
						} else if (url.startsWith("http:") || url.startsWith("https:")) {
							// Normal urls
							// can't use removeJavascriptInterface on 2.x
							subView.addJavascriptInterface(new Object(), "__forge");
							if (ForgeApp.appConfig.has("trusted_urls")) {
								for (JsonElement whitelistPattern : ForgeApp.appConfig.getAsJsonArray("trusted_urls")) {
									if (ForgeUtil.urlMatchesPattern(url, whitelistPattern.getAsString())) {
										ForgeLog.i("Enabling forge JavaScript API for whitelisted URL in tabs browser: "+url);
										subView.addJavascriptInterface(new ForgeJSBridge(subView), "__forge");
										break;
									}
								}
							}
							return false;
						} else {
							// Some other URI scheme, let the phone handle it if
							// possible
							Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
							final PackageManager packageManager = ForgeApp.getActivity().getPackageManager();
							List<ResolveInfo> list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
							if (list.size() > 0) {
								// Intent exists, invoke it.
								ForgeLog.i("Allowing another Android app to handle URL: " + url);
								ForgeApp.getActivity().startActivity(intent);
							} else {
								ForgeLog.w("Attempted to open a URL which could not be handled: " + url);
							}
							return true;
						}
					}

					@Override
					public void onLoadResource(WebView view, String url) {
						String viewUrl = view.getUrl();
						checkMatchPattern(viewUrl);
					}

					private void checkMatchPattern(String url) {
						if (url != null && fPattern != null && url.matches(fPattern) && view != null) {
							ForgeLog.i("Match pattern hit in modal view, closing and returning current URL.");
							closeModal(ForgeApp.getActivity(), url, false);
						}
					}
				});

				// Check for whitelisted remote URLs
				if (ForgeApp.appConfig.has("trusted_urls")) {
					for (JsonElement whitelistPattern : ForgeApp.appConfig.getAsJsonArray("trusted_urls")) {
						if (ForgeUtil.urlMatchesPattern(url, whitelistPattern.getAsString())) {
							ForgeLog.i("Enabling forge JavaScript API for whitelisted URL in tabs browser: "+url);
							subView.addJavascriptInterface(new ForgeJSBridge(subView), "__forge");
							break;
						}
					}
				}
				
				subView.loadUrl(url);

				// Add to the view group and switch
				ForgeApp.getActivity().addModalView(layout);
				subView.requestFocus(View.FOCUS_DOWN);
				
				task.success(task.callid);
			}
		});
	}
	
	public void close() {
		closeModal(ForgeApp.getActivity(), webView.getUrl(), false);
	}
}