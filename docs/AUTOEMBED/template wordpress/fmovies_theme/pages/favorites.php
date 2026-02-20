<?php
/**
 * Template Name: favorites
 * 
 * A custom page template for favorites.
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

get_header(); ?>
<div class="container mt-5">
    <section class="bl">
        <div class="heading"> 
            <?php the_title( '<h1>', '</h1>' ); ?> 
            <div class="clearfix"></div> 
        </div><!-- #heading -->
        <div class="content">
            <div class="filmlist md active">
                <div id="page-favorites"></div>
                <div class="clearfix"></div>
            </div>
            <div class="clearfix"></div>
        </div><!-- #content -->
    </section><!-- #section -->
</div><!-- #container -->
<?php get_footer(); ?>