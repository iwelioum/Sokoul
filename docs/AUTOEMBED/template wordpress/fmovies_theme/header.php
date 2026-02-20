<?php
/**
 * The header for our theme
 *
 * This is the template that displays all of the <head> section and everything up until <div id="body">
 *
 * @link https://developer.wordpress.org/themes/basics/template-files/#template-partials
 *
 * @package fmovie
 */

?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
<meta charset="<?php bloginfo( 'charset' ); ?>" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
 <script disable-devtool-auto src="https://fastly.jsdelivr.net/npm/disable-devtool/disable-devtool.min.js"></script>
<?php wp_head(); ?>

</head>
<body <?php body_class(); ?>>
<?php wp_body_open(); ?>
<header id="masthead" <?php header_class(); ?>>
	<div class="container">
		<div id="menu-toggler">
		<i class="fa fa-list-ul"></i>
		</div> 
        <?php get_template_part( 'template-parts/header/site-header' ); ?>
		<?php get_template_part( 'template-parts/header/site-login' ); ?>
		<?php get_search_form(); ?>
	</div>
</header><!-- #site-header -->
<div id="body">

