//
//  modalWebViewController.m
//  Forge
//
//  Created by Connor Dunn on 27/07/2011.
//  Copyright 2011 __MyCompanyName__. All rights reserved.
//

#import "tabs_modalWebViewController.h"

@implementation tabs_modalWebViewController
@synthesize navigationItem;
@synthesize title;


- (void)didReceiveMemoryWarning
{
	// Releases the view if it doesn't have a superview.
	[super didReceiveMemoryWarning];
}

#pragma mark - View lifecycle

- (void)viewDidLoad
{
	[super viewDidLoad];
	
	[backButton setAction:@selector(cancel:)];
	
	if (url == nil) {
		url = [NSURL URLWithString:@"https://trigger.io"];
	}
	[webView loadRequest:[NSURLRequest requestWithURL:url]];
	if (backImage != nil) {
		[[[ForgeFile alloc] initWithObject:backImage] data:^(NSData *data) {
			UIImage *icon = [[UIImage alloc] initWithData:data];
			icon = [icon imageWithWidth:0 andHeight:28 andRetina:YES];
			[backButton setImage:icon];

		} errorBlock:^(NSError *error) {
		}];
	} else {
		[backButton setTitle:backLabel];
	}
	[navigationItem setTitle:title];
    
	if (tint != nil && [navBar respondsToSelector:@selector(setBarTintColor:)]) {
        [navBar setBarTintColor:tint];
	} else if (tint != nil && [navBar respondsToSelector:@selector(setTintColor:)]) {
		[navBar setTintColor:tint];
	}

    if (titleTint != nil && [navBar respondsToSelector:@selector(setTitleTextAttributes:)]) {
        [navBar setTitleTextAttributes:@{ NSForegroundColorAttributeName:titleTint }];
    }
    
	if (buttonTint != nil && [backButton respondsToSelector:@selector(setTintColor:)]) {
		[backButton setTintColor:buttonTint];
	}
	
	int height = 44;
	if (floor(NSFoundationVersionNumber) > NSFoundationVersionNumber_iOS_6_1) {
		height += [ForgeApp sharedApp].webviewTop;
		navBar.frame = CGRectMake(navBar.frame.origin.x, navBar.frame.origin.y + [ForgeApp sharedApp].webviewTop, navBar.frame.size.width, navBar.frame.size.height);
	}
	[webView.scrollView setContentInset:UIEdgeInsetsMake(height, 0, 0, 0)];
	[webView.scrollView setScrollIndicatorInsets:UIEdgeInsetsMake(height, 0, 0, 0)];
}

- (void) viewDidDisappear:(BOOL)animated {
	// Make sure the network indicator is turned off
	[UIApplication sharedApplication].networkActivityIndicatorVisible = NO;

	if (returnObj != nil) {
		[[ForgeApp sharedApp] event:[NSString stringWithFormat:@"tabs.%@.closed", task.callid] withParam:returnObj];
	}
}

- (void)stringByEvaluatingJavaScriptFromString:(ForgeTask*)evalTask string:(NSString*)string {
	[evalTask success:[webView stringByEvaluatingJavaScriptFromString:string]];
}

- (void)cancel:(id)nothing {
	returnObj = [NSDictionary dictionaryWithObjectsAndKeys:
							   [NSNumber numberWithBool:YES],
							   @"userCancelled",
							   nil
							   ];

	[[[ForgeApp sharedApp] viewController] dismissModalViewControllerAnimated:YES];
	[[[ForgeApp sharedApp] viewController] performSelector:@selector(dismissModalViewControllerAnimated:) withObject:[NSNumber numberWithBool:YES] afterDelay:0.5f];
}

- (void)setUrl:(NSURL*)newUrl {
	url = newUrl;
}
- (void)setRootView:(UIViewController*)newRootView {
	rootView = newRootView;
}
- (void)setTitle:(NSString *)newTitle {
	title = newTitle;
}
- (void)setBackLabel:(NSString *)newBackLabel {
	backLabel = newBackLabel;
}
- (void)setBackImage:(NSString *)newBackImage {
	backImage = newBackImage;
}
- (void)setTask:(ForgeTask *)newTask {
	task = newTask;
}
- (void)setPattern:(NSString *)newPattern {
	pattern = newPattern;
}
- (void)setTitleTintColor:(UIColor *)newTint {
    titleTint = newTint;
}
- (void)setTintColor:(UIColor *)newTint {
	tint = newTint;
}
- (void)setButtonTintColor:(UIColor *)newTint {
	buttonTint = newTint;
}

- (void)viewDidUnload {
	[super viewDidUnload];
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)interfaceOrientation {
	// Return YES for supported orientations
	return YES;
}

- (BOOL)webView:(UIWebView *)myWebView shouldStartLoadWithRequest:(NSURLRequest *)request navigationType:(UIWebViewNavigationType)navigationType {
	// Called when a URL is requested
	
	NSURL *thisurl = [request URL];

	if ([[thisurl scheme] isEqualToString:@"forge"]) {
		if ([[thisurl absoluteString] isEqualToString:@"forge://go"]) {
			// See if URL is whitelisted - only allow forge API access on trusted pages
			BOOL safe = NO;
			for (NSString *whitelistedPattern in (NSArray*)[[[[[ForgeApp sharedApp] appConfig] objectForKey:@"core"] objectForKey:@"general"] objectForKey:@"trusted_urls"]) {
				if ([ForgeUtil url:[myWebView.request.URL absoluteString] matchesPattern:whitelistedPattern]) {
					[ForgeLog d:[NSString stringWithFormat:@"Allowing forge JavaScript API access for whitelisted URL in tabs browser: %@", [url absoluteString]]];
					safe = YES;
					break;
				}
			}
			if (!safe) {
				return NO;
			}
			
			[myWebView stringByEvaluatingJavaScriptFromString:@"window.forge._flushingInterval && clearInterval(window.forge._flushingInterval)"];
			NSString *jsResult;
			do {
				// Get the Javascript call queue
				jsResult = [myWebView stringByEvaluatingJavaScriptFromString:@"window.forge._get()"];

				// Loop over each of the returned objects
				for (NSDictionary* object in [jsResult objectFromJSONString]) {
					[ForgeLog d:[NSString stringWithFormat:@"Native call in modal view: %@", object]];
					[BorderControl runTask:object forWebView:myWebView];
				}
			} while ([jsResult length] > 4);
			[myWebView stringByEvaluatingJavaScriptFromString:@"window.forge._flushing = false;"];
			
			// Prevent page load
			return NO;
		}

		returnObj = [NSDictionary dictionary];
		[[[ForgeApp sharedApp] viewController] dismissModalViewControllerAnimated:YES];
		[[[ForgeApp sharedApp] viewController] performSelector:@selector(dismissModalViewControllerAnimated:) withObject:[NSNumber numberWithBool:YES] afterDelay:0.5f];
		
		return NO;
	} else {
		if (pattern != nil) {
			NSRegularExpression *regex = [NSRegularExpression regularExpressionWithPattern:pattern options:NSRegularExpressionCaseInsensitive error:nil];
			if ([regex numberOfMatchesInString:[thisurl absoluteString] options:0 range:NSMakeRange(0, [[thisurl absoluteString] length])] > 0) {
				returnObj = [NSDictionary dictionaryWithObjectsAndKeys:
										   [thisurl absoluteString],
										   @"url",
										   [NSNumber numberWithBool:NO],
										   @"userCancelled",
										   nil
										   ];
				
				[[[ForgeApp sharedApp] viewController] dismissModalViewControllerAnimated:YES];
				[[[ForgeApp sharedApp] viewController] performSelector:@selector(dismissModalViewControllerAnimated:) withObject:[NSNumber numberWithBool:YES] afterDelay:0.5f];
			
				return NO;
			}
		}
		return YES;
	}
}

- (void)webViewDidStartLoad:(UIWebView *)_webView {
	[UIApplication sharedApplication].networkActivityIndicatorVisible = YES;
	[[ForgeApp sharedApp] event:[NSString stringWithFormat:@"tabs.%@.loadStarted", task.callid] withParam:@{@"url": webView.request.URL.absoluteString}];
}

- (void)webViewDidFinishLoad:(UIWebView *)_webView {
	[UIApplication sharedApplication].networkActivityIndicatorVisible = NO;
	[[ForgeApp sharedApp] event:[NSString stringWithFormat:@"tabs.%@.loadFinished", task.callid] withParam:@{@"url": webView.request.URL.absoluteString}];
}

- (void)webView:(UIWebView *)myWebView didFailLoadWithError:(NSError *)error {
	[UIApplication sharedApplication].networkActivityIndicatorVisible = NO;
	
	if (error.code == -1009) {
		UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error loading"
														message:@"No Internet connection available."
													   delegate:nil 
											  cancelButtonTitle:@"OK"
											  otherButtonTitles:nil];
		[alert show];
	}
	[ForgeLog w:[NSString stringWithFormat:@"Modal webview error: %@", error]];
	[[ForgeApp sharedApp] event:[NSString stringWithFormat:@"tabs.%@.loadError", task.callid] withParam:@{@"url": webView.request.URL.absoluteString, @"description": error.description}];
}

-(UIBarPosition)positionForBar:(id<UIBarPositioning>)bar {
	return UIBarPositionTopAttached;
}

-(void)close {
	returnObj = [NSDictionary dictionaryWithObjectsAndKeys:
							   webView.request.URL.absoluteString,
							   @"url",
							   [NSNumber numberWithBool:NO],
							   @"userCancelled",
							   nil
							   ];
	
	[[[ForgeApp sharedApp] viewController] dismissModalViewControllerAnimated:YES];
	[[[ForgeApp sharedApp] viewController] performSelector:@selector(dismissModalViewControllerAnimated:) withObject:[NSNumber numberWithBool:YES] afterDelay:0.5f];
}

- (BOOL)prefersStatusBarHidden {
	return [[ForgeApp sharedApp].viewController prefersStatusBarHidden];
}

@end
