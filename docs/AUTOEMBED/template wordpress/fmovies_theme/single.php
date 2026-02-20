<?php
/**
 * The template for displaying all single posts
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/#single-post
 *
 * @package fmovie
 */

get_header();
// Start the Loop.
while ( have_posts() ) :
the_post();
setPostViews(get_the_ID());
get_template_part( 'template-parts/content/content', 'single' );
endwhile; 
// End the loop.
get_footer();