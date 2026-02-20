<?php
/**
 * Template part for displaying page content in page.php
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="container mt-4">
    <section class="bl">
        <div class="heading"> 
            <?php the_title( '<h2>', '</h2>' ); ?>
            <div class="clearfix"></div> 
        </div><!-- #heading -->
        <div class="content">
            <?php the_content(); ?>
            <?php wp_link_pages(); ?>
            <div class="clearfix"></div>
        </div><!-- #content -->
    </section><!-- #section -->
</div><!-- #container -->







