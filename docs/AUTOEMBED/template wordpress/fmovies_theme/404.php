<?php
/**
 * The template for displaying 404 pages (not found)
 *
 * @link https://codex.wordpress.org/Creating_an_Error_404_Page
 *
 * @package fmovie
 */

get_header();
?>
<div class="container">
  <div class="error-page">
    <h2><?php echo esc_html__( '404', 'fmovie' ); ?></h2>
    <div class="message"> <?php echo esc_html__( 'Oops, sorry what you are looking for does not exist or has not yet been added.', 'fmovie' ); ?></div><a href="<?php echo esc_url( home_url( '/' ) ); ?>" class="home btn btn-primary"><i class="fas fa-arrow-circle-left"></i> <?php echo esc_html__( 'Back to home page', 'fmovie' ); ?></a>
  </div><!-- #error-page -->
</div><!-- #container -->
<?php get_footer(); ?>