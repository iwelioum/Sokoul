<?php
/**
 * Template part for widgets on footer
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="links">
	<?php if ( is_active_sidebar( 'sidebar-1' ) ) : ?>
	<?php dynamic_sidebar( 'sidebar-1' ); ?>
	<?php endif; ?>
	<?php if ( is_active_sidebar( 'sidebar-2' ) ) : ?>
	<?php dynamic_sidebar( 'sidebar-2' ); ?>
	<?php endif; ?>
	<?php if ( is_active_sidebar( 'sidebar-3' ) ) : ?>
	<?php dynamic_sidebar( 'sidebar-3' ); ?>
	<?php endif; ?>
	<div class="clearfix"></div>
</div><!-- .links -->
