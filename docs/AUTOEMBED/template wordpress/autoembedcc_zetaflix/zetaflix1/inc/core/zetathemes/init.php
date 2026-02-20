<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*
*/


/* Omgdb Plugin
========================================================
*/
require_once(ZETA_DIR.'/inc/core/omgdb/init.php');

/* Zetathemes class
========================================================
*/
if(!class_exists('Zetathemes')){
	get_template_part('inc/core/zetathemes/class');
}

/* Theme Updater
========================================================
*/
new Zetathemes(

	// Main data
	$config = array(
		'item_name'		 => ZETA_THEME,
		'theme_slug'	 => ZETA_THEME_SLUG,
		'version'		 => ZETA_VERSION,
		'author'		 => ZETA_COM,
		'download_id'	 => ZETA_ITEM_ID,
        'remote_api_url' => 'https://cdn.bescraper.cf/api',
		'renew_url'		 => 'https://cdn.bescraper.cf/api'
	),

	// Texts
	$strings = array(
    	'theme-license'				=> ZETA_THEME .' '. __z('license'),
    	'enter-key'					=> __z('Enter your theme license key.'),
    	'license-key'				=> __z('License Key'),
    	'license-action'			=> __z('License Action'),
    	'deactivate-license'		=> __z('Deactivate License'),
    	'activate-license'			=> __z('Activate License'),
    	'status-unknown'			=> __z('License status is unknown.'),
    	'renew'						=> __z('Renew?'),
    	'unlimited'					=> __z('unlimited'),
    	'license-key-is-active'		=> __z('License key is active'),
    	'expires%s'					=> __z('since %s.'),
    	'%1$s/%2$-sites'			=> __z('You have %1$s / %2$s sites activated.'),
    	'license-key-expired-%s'	=> __z('License key expired %s.'),
    	'license-key-expired'		=> __z('License key has expired.'),
    	'license-keys-do-not-match' => __z('License keys do not match.'),
    	'license-is-inactive'		=> __z('License is inactive.'),
    	'license-key-is-disabled'	=> __z('License key is disabled.'),
    	'site-is-inactive'			=> __z('Please activate a valid license.'),
    	'license-status-unknown'	=> __z('License status is unknown.'),
    	'update-notice'				=> __z('Updating this theme will lose any customizations you have made. \'Cancel\' to stop, \'OK\' to update.'),
    	'update-available'			=> __z('<strong>%1$s %2$s</strong> is available. <a href="%3$s" class="thickbox" title="%4s">Check out what\'s new</a> or <a href="%5$s"%6$s>update now</a>.')
	)
);

function theme_changeloger_check() {
    if (is_admin() && isset($_GET['theme']) && $_GET['theme']  === ZETA_THEME_SLUG && isset($_GET['view']) && $_GET['view'] === 'changelog') {
            // Display a simple white page with the text "test"
			$changelog = ZETA_SERVER.'/'.ZETA_THEME_TYPE.'/'.ZETA_THEME_SLUG.'/?view=updatelog';
			$response = wp_remote_get($changelog);	
			if (is_wp_error($response)) {		
				echo 'Error occured while fetching data, try again later or contact support.';
			}else{
				$html_content = wp_remote_retrieve_body($response);
				echo $html_content;			
			}        	
			exit;
    }
}
add_action('admin_init', 'theme_changeloger_check');
