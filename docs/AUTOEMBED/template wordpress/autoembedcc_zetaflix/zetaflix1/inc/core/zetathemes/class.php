<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

/* Zetathemes Class
========================================================
*/
class Zetathemes {

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	protected $remote_api_url	= null;
	protected $theme_slug		= null;
	protected $version			= null;
	protected $author			= null;
	protected $download_id		= null;
	protected $renew_url		= null;
	protected $strings			= null;
	protected $item_name		= null;

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function __construct($config = array(), $strings = array()){
		# config
		$config = wp_parse_args($config,array(
			'remote_api_url' => '',
			'theme_slug'	 => get_template(),
			'item_name'		 => '',
			'license'		 => '',
			'version'		 => '',
			'author'		 => '',
			'download_id'	 => '',
			'renew_url'		 => ''
		));
		$this->remote_api_url = $config['remote_api_url'];
		$this->item_name	  = $config['item_name'];
		$this->theme_slug	  = sanitize_key( $config['theme_slug'] );
		$this->version		  = $config['version'];
		$this->author		  = $config['author'];
		$this->download_id	  = $config['download_id'];
		$this->renew_url	  = $config['renew_url'];
		if('' == $config['version']){
			$theme = wp_get_theme( $this->theme_slug );
			$this->version = $theme->get('Version');
		}
		$this->strings = $strings;
		add_action('admin_init', array( $this, 'updater') );
		add_action('admin_init', array( $this, 'register_option') );
		add_action('admin_init', array( $this, 'license_action') );
		add_action('admin_menu', array( $this, 'license_menu') );
		add_action('update_option_' . $this->theme_slug . '_license_key', array( $this, 'activate_license'), 10, 2 );
		add_filter('http_request_args', array( $this, 'disable_wporg_request'), 5, 2 );
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function updater() {

		if(get_option( $this->theme_slug . '_license_key_status', false) != 'valid'){
			return;
		}
		if(!class_exists('Zetathemes_updater')){
			get_template_part('inc/core/zetathemes/updater');
		}
		new Zetathemes_updater(
			array(
				'remote_api_url' 	=> $this->remote_api_url,
				'version' 			=> $this->version,
				'license' 			=> trim(get_option( $this->theme_slug.'_license_key')),
				'item_name' 		=> $this->item_name,
				'author'			=> $this->author
			),
			$this->strings
		);
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function license_menu(){
		$strings = $this->strings;
		add_theme_page($strings['theme-license'], __z('ZetaFlix License'), 'manage_options', $this->theme_slug.'-license', array( $this, 'license_page'));
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function license_page(){
		$strings	= $this->strings;
		$license	= trim( get_option( $this->theme_slug . '_license_key') );
		$idkey		= $this->theme_slug . '_license_key';
		$status		= get_option( $this->theme_slug . '_license_key_status', false );
		if ( ! $license ) {
			$message    = $strings['enter-key'];
		} else {
			if ( ! get_transient( $this->theme_slug . '_license_message', false ) ) {
				set_transient( $this->theme_slug . '_license_message', $this->check_license(), ( 60 * 60 * 24 ) );
			}
			$message = get_transient( $this->theme_slug . '_license_message');
		}
		// Start page
		echo '<div class="wrap about-wrap h2_zt_boxs">';
		echo '<h1>License <strong>'. $this->item_name . ' ' . $this->version . '</strong></h1>';
		echo '<div class="about-text"><strong>'. $this->item_name  .'</strong> '. __z('requires a valid license to activate all functions.'). '</div>';
		echo '<h2 class="nav-tab-wrapper wp-clearfix">';
		echo '<a data-tab="form" class="nav-tab zeta-nav-tab nav-tab-active">'. __z('Activate license'). '</a>';
		echo '</h2>';
		// Form
		echo '<div id="form" class="zt_boxg current">';
		echo '<form method="post" action="options.php">';
		// Setting fields
		settings_fields( $this->theme_slug . '-license');
		echo '<table class="form-table"><tbody>';
		echo '<tr><td>';
		echo '<input id="'.$idkey.'" name="'.$idkey.'" type="text" class="zt_text_license zt_'.$status.'" value="'. esc_attr($license).'" placeholder="'. __z('License'). '"/>';
		// WP_nonce
		wp_nonce_field( $this->theme_slug . '_nonce', $this->theme_slug . '_nonce');
		// License message
		echo '<span class="status_zt_license">'. $message .'</span>';
		// WP_nonce
		wp_nonce_field( $this->theme_slug . '_nonce', $this->theme_slug . '_nonce');
		// Active message
		if ('valid' == $status ) {
			echo '<div class="changelog zt_div_fix"><h2 class="zt_text_h2">'. __z('License activated, thanks.'). '</h2></div>';
		} else {
			echo '<div class="changelog zt_div_fix"><h2 class="zt_text_h2">'. __z('Activate your license to install future updates.'). '</h2></div>';
		}
		echo '</td></tr>';
		echo '<tr><td>';
		echo '<input type="submit" name="submit" id="submit" class="button button-primary" value="'. __z('Save changes'). '">&nbsp;';
		// Activate or Deactivate
		if ( $license ) {
			wp_nonce_field( $this->theme_slug . '_nonce', $this->theme_slug . '_nonce');
			if ('valid' == $status ) {
				echo '<input type="submit" class="button-secondary" name="'. $this->theme_slug. '_license_deactivate" value="'. __z('Deactivate license'). '"/>';
			} else {
				echo '<input type="submit" class="button-secondary" name="'. $this->theme_slug. '_license_activate" value="'. __z('Activate license'). '"/>';
			}
		}
		echo '</td></tr>';
		echo '</tbody></table></form></div>';
		echo '</div>';
		// End page
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function register_option() {
		register_setting($this->theme_slug . '-license', $this->theme_slug . '_license_key', array( $this, 'sanitize_license') );
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function sanitize_license( $new ) {
		$old = get_option( $this->theme_slug . '_license_key');
		if ( $old && $old != $new ) {
			// New license has been entered, so must reactivate
			delete_option( $this->theme_slug . '_license_key_status');
			delete_transient( $this->theme_slug . '_license_message');
		}

		return $new;
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function get_api_response($api_params){
		$response = wp_remote_get($this->remote_api_url, array('timeout' => 15, 'sslverify' => true, 'body' => $api_params));
		if(is_wp_error($response)){
			return false;
		}
		$response = json_decode(wp_remote_retrieve_body($response));
		return $response;
	 }

	 /**
 	 * @since 1.0.0
 	 * @version 1.1
 	 */
	function activate_license() {
		$license = trim(get_option($this->theme_slug.'_license_key'));
		$api_params = array(
			'edd_action' => 'activate_license',
			'license'    => $license,
			'item_name'  => urlencode( $this->item_name )
		);
		$license_data = $this->get_api_response( $api_params );
		if ( $license_data && isset( $license_data->license ) ) {
			update_option( $this->theme_slug . '_license_key_status', $license_data->license );
			delete_transient( $this->theme_slug . '_license_message');
		}
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function deactivate_license() {
		$license = trim( get_option( $this->theme_slug . '_license_key') );
		$api_params = array(
			'edd_action' => 'deactivate_license',
			'license'    => $license,
			'item_name'  => urlencode( $this->item_name )
		);
		$license_data = $this->get_api_response( $api_params );
		if ( $license_data && ( $license_data->license == 'deactivated') ) {
			delete_option( $this->theme_slug . '_license_key_status');
			delete_transient( $this->theme_slug . '_license_message');
		}
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function get_renewal_link() {
		if ('' != $this->renew_url ) {
			return $this->renew_url;
		}
		$license_key = trim( get_option( $this->theme_slug . '_license_key', false ) );
		if ('' != $this->download_id && $license_key ) {
			$url = esc_url( $this->remote_api_url );
			$url .= '/checkout/?edd_license_key=' . $license_key . '&download_id=' . $this->download_id;
			return $url;
		}
		return $this->remote_api_url;
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function license_action() {
		# Activate license action
		if ( isset( $_POST[ $this->theme_slug . '_license_activate' ] ) ) {
			if ( check_admin_referer( $this->theme_slug . '_nonce', $this->theme_slug . '_nonce') ) {
				$this->activate_license();
			}
		}

		# Deactivate license action
		if ( isset( $_POST[$this->theme_slug . '_license_deactivate'] ) ) {
			if ( check_admin_referer( $this->theme_slug . '_nonce', $this->theme_slug . '_nonce') ) {
				$this->deactivate_license();
			}
		}
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function check_license() {
		$license = trim( get_option( $this->theme_slug . '_license_key') );
		$strings = $this->strings;
		$api_params = array(
			'edd_action' => 'check_license',
			'license'    => $license,
			'item_name'  => urlencode( $this->item_name ),
			'url'        => home_url()
		);
		$license_data = $this->get_api_response( $api_params );
		if ( !isset( $license_data->license ) ) {
			$message = $strings['license-unknown'];
			return $message;
		}
		if ( $license_data && isset( $license_data->license ) ) {
			update_option( $this->theme_slug . '_license_key_status', $license_data->license );
		}
		$expires = false;

		if ( isset( $license_data->expires ) ) {
			$expires = date_i18n( get_option('date_format'), strtotime( $license_data->expires ) );
			$renew_link = '<a href="' . esc_url( $this->get_renewal_link() ) . '" target="_blank">' . $strings['renew'] . '</a>';
		}
		$site_count = $license_data->site_count;
		$license_limit = $license_data->license_limit;
		if ( 0 == $license_limit ) {
			$license_limit = $strings['unlimited'];
		}
		if ( $license_data->license == 'valid') {
			$message = $strings['license-key-is-active'] . ' ';
			if ( $expires ) {
				$message .= sprintf( $strings['expires%s'], $expires ) . ' ';
			}

			if ( $site_count && $license_limit ) {
				$message .= sprintf( $strings['%1$s/%2$-sites'], $site_count, $license_limit );
			}
		} else if ( $license_data->license == 'expired') {
			if ( $expires ) {
				$message = sprintf( $strings['license-key-expired-%s'], $expires );
			} else {
				$message = $strings['license-key-expired'];
			}
			if ( $renew_link ) {
				$message .= ' ' . $renew_link;
			}
		} else if ( $license_data->license == 'invalid') {
			$message = $strings['license-keys-do-not-match'];
		} else if ( $license_data->license == 'inactive') {
			$message = $strings['license-is-inactive'];
		} else if ( $license_data->license == 'disabled') {
			$message = $strings['license-key-is-disabled'];
		} else if ( $license_data->license == 'site_inactive') {
			$message = $strings['site-is-inactive'];
		} else {
			$message = $strings['license-status-unknown'];
		}
		return $message;
	}

	/**
	 * @since 1.0.0
	 * @version 1.1
	 */
	function disable_wporg_request($r, $url){
		if(0 !== strpos($url,'https://api.wordpress.org/themes/update-check/1.1/')){
 			return $r;
 		}
 		$themes = json_decode( $r['body']['themes'] );
 		$parent = get_option('template');
 		$child	= get_option('stylesheet');
 		unset( $themes->themes->$parent );
 		unset( $themes->themes->$child );
 		$r['body']['themes'] = json_encode($themes);
		// the return
 		return $r;
	}
	// End Class
}
