<?php
/**
 * Theme plugins
 *
 * @package fmovie
 */

require_once get_template_directory() . '/inc/tgmpa.php';

add_action( 'tgmpa_register', 'fmovie_register_required_plugins' );

function fmovie_register_required_plugins() {

	$plugins = array(


		array(
			'name'               => 'Fmovie Core', 
			'slug'               => 'fmovie-core',
			'source'             => 'https://github.com/Tommy0412/fmovies_plugins/raw/main/fmovie-core.zip', 
			'required'           => true, 
			'version'            => '1.0.3', 
			'force_activation'   => true,
			'force_deactivation' => true, 
			'external_url'       => '', 
			'is_callable'        => '', 
		),
		array(
			'name'               => 'Custom Post Templates', 
			'slug'               => 'custom-post-template', 
			'source'             => 'https://github.com/Tommy0412/fmovies_plugins/raw/main/custom-post-template.zip',
			'required'           => true, 
			'version'            => '1.6',
			'force_activation'   => true,
			'force_deactivation' => true,
			'external_url'       => '', 
			'is_callable'        => '', 
		),
		array(
			'name'               => 'Advanced Custom Fields PRO',
			'slug'               => 'advanced-custom-fields-pro', 
			'source'             => 'https://github.com/Tommy0412/fmovies_plugins/raw/main/advanced-custom-fields-pro.zip',
			'required'           => true, 
			'version'            => '6.0.7', 
			'force_activation'   => true,
			'force_deactivation' => true, 
			'external_url'       => '', 
			'is_callable'        => '', 
		),
		array(
			'name'               => 'Importer', 
			'slug'               => 'Importer', 
			'source'             => 'https://github.com/Tommy0412/fmovies_plugins/raw/main/Importer.zip', 
			'required'           => false, 
			'version'            => '', 
			'force_activation'   => false, 
			'force_deactivation' => true,
			'external_url'       => '', 
			'is_callable'        => '', 
		),
		array(
			'name'               => 'Report Content',
			'slug'               => 'report-content', 
			'source'             => 'https://github.com/Tommy0412/fmovies_plugins/raw/main/report-content.zip', 
			'required'           => true,
			'version'            => '', 
			'force_activation'   => true, 
			'force_deactivation' => true, 
			'external_url'       => '', 
			'is_callable'        => '', 
		),
		
	);
	$config = array(
		'id'           => 'fmovie',                
		'default_path' => '',                      
		'menu'         => 'tgmpa-install-plugins', 
		'parent_slug'  => 'themes.php',            
		'capability'   => 'edit_theme_options',    
		'has_notices'  => true,                    
		'dismissable'  => true,                    
		'dismiss_msg'  => '',                      
		'is_automatic' => false,                   
		'message'      => '',                   
	);

	tgmpa( $plugins, $config );
}
