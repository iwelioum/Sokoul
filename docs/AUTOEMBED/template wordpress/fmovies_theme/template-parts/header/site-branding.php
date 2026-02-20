<?php
/**
 * Displays header site branding
 *
 * @package fmovie
 */
$blog_info    = get_bloginfo( 'name' );
$header_class = 'site-title';
?>

<?php if( function_exists( 'the_custom_logo' ) ) { if( has_custom_logo() ) { ?>
	<?php the_custom_logo(); ?>
	<?php } else { ?>
	<?php if ( $blog_info ) : ?>
		<?php if ( is_front_page() && ! is_paged() ) : ?>
			<h1 class="<?php echo esc_attr( $header_class ); ?>"><a href="<?php echo esc_url( home_url( '/home' ) ); ?>"><?php echo esc_html( $blog_info ); ?></a></h1>
		<?php elseif ( is_front_page() && ! is_home() ) : ?>
			<h1 class="<?php echo esc_attr( $header_class ); ?>"><a href="<?php echo esc_url( home_url( '/home' ) ); ?>"><?php echo esc_html( $blog_info ); ?></a></h1>
		<?php else : ?>
			<p class="<?php echo esc_attr( $header_class ); ?>"><a href="<?php echo esc_url( home_url( '/home' ) ); ?>"><?php echo esc_html( $blog_info ); ?></a></p>
		<?php endif; ?>
	<?php endif; ?>
<?php } ?>
<?php } ?>