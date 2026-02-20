<?php
/**
 * admin core
 *
 * @package fmovie
 */

/* Exit if accessed directly */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

get_template_part('admin/options');

function admin_add_styles() {
	
	wp_register_style('fmovies-style', get_template_directory_uri() . '/admin/css/fmovies.css', array(), wp_get_theme()->get( 'Version' ), 'all');
	
	wp_enqueue_style('fmovies-style');
}
function admin_add_scripts() {
	
	wp_register_script('admin-scripts', get_template_directory_uri() . '/admin/js/admin.js', array('jquery'), wp_get_theme()->get( 'Version' ), false);
	
	wp_enqueue_script('admin-scripts');
}
add_action('admin_enqueue_scripts', 'admin_add_styles');
add_action('admin_enqueue_scripts', 'admin_add_scripts');

function admin_init(){
	admin_options('add_option');
}

function admin_menu() {
	add_menu_page(__('Fmovies', 'fmovie'), __('Fmovies', 'fmovie'), 'manage_options', 'admin-main', 'admin_render_main', 'dashicons-controls-play', 61);
	add_submenu_page('admin-main', __('General', 'fmovie'), __('General', 'fmovie'), 'manage_options', 'admin-main', 'admin_render_main');
	add_submenu_page('admin-main', __('Home', 'fmovie'), __('Home', 'fmovie'), 'manage_options', 'admin-home', 'admin_render_home');
	add_submenu_page('admin-main', __('Branding', 'fmovie'), __('Branding', 'fmovie'), 'manage_options', 'admin-branding', 'admin_render_branding');
	add_submenu_page('admin-main', __('Translate', 'fmovie'), __('Translate', 'fmovie'), 'manage_options', 'admin-translate', 'admin_render_translate');
	add_submenu_page('admin-main', __('Comments', 'fmovie'), __('Comments', 'fmovie'), 'manage_options', 'admin-comments', 'admin_render_comments');
	add_submenu_page('admin-main', __('Advertising', 'fmovie'), __('Advertising', 'fmovie'), 'manage_options', 'admin-advertising', 'admin_render_advertising');
    add_submenu_page('admin-main', __('Player', 'fmovie'), __('Player', 'fmovie'), 'manage_options', 'admin-player', 'admin_render_player');
	add_submenu_page('admin-main', __('Reset', 'fmovie'), __('Reset', 'fmovie'), 'manage_options', 'admin-reset', 'admin_render_reset');
}
function admin_render_main() {
	get_template_part('admin/page-main');	
}
function admin_render_home() {
	get_template_part('admin/page-home');	
}
function admin_render_branding() {
	get_template_part('admin/page-branding');	
}
function admin_render_translate() {
	get_template_part('admin/page-translate');	
}
function admin_render_comments() {
	get_template_part('admin/page-comments');	
}
function admin_render_advertising() {
	get_template_part('admin/page-advertising');	
}
function admin_render_player() {
	get_template_part('admin/page-player');	
}
function admin_render_reset() {
	get_template_part('admin/page-reset');	
}
add_action('admin_init', 'admin_init');
add_action('admin_menu', 'admin_menu');