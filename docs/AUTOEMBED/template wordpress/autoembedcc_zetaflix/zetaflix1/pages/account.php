<?php
/*
Template Name: ZT - Account page
*/


if(!current_user_can('subscriber') OR zeta_is_true('permits','eusr') == true) {

    if(is_user_logged_in()):
    	get_template_part('pages/sections/account');
    else:
    	get_template_part('pages/sections/zt_head');
    	if(isset($_GET['action']) and $_GET['action'] =='signup'):
    		get_template_part('pages/sections/register');
    	else:
    		get_template_part('pages/sections/login');
    	endif;
    	get_template_part('pages/sections/zt_foot');
    endif;

} else {

    wp_die( __z('You do not have permission to access this page'), __z('Module disabled'));

}
