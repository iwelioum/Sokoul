<?php
/**
 * Displays servers
 *
 * @package fmovie
 */

$fmovie_premium = get_option('admin_premium');
$fmovie_admin_server_2 = get_option('admin_server_2');
$fmovie_admin_server_3 = get_option('admin_server_3');
?>
<div id="episodes">
	<?php if ( is_post_template( 'tv.php' ) ) { ?>			
		<?php get_template_part( 'template-parts/watch/content-seasons' ); ?>
		<?php } else { ?>
		<div id="servers">
			<div class="note">
				<?php echo textautoembed; ?>
			</div>
			<?php if ( get_field( 'manual_movies' ) == 1 ) : ?>
			<?php if ( have_rows( 'iframe' ) ) : ?>
			<?php while ( have_rows( 'iframe' ) ) : the_row(); ?>
			<div id="manual" class="server <?php the_sub_field( 'host' ); ?>" onclick="loadEmbed('<?php the_sub_field( 'server' ); ?>');">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php the_sub_field( 'host' ); ?></div>
			</div>
			<?php endwhile; ?>
			<?php else : ?>
			<?php // No rows found ?>
			<?php endif; ?>
			<?php else : ?>
            <?php if ($fmovie_premium == 1) {  ?>
			<!-- premium api -->
			<div class="server active" onclick="loadServer(premium)">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php echo server_0_text; ?></div>
			</div>
			<!-- #premium api -->
			<div class="server" onclick="loadServer(embedru)">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php echo server_1_text; ?></div>
			</div>
            <?php } else { ?>
			<div class="server active" onclick="loadServer(embedru)">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php echo server_1_text; ?></div>
			</div>
            <?php } ?>
			<?php if ($fmovie_admin_server_2 == 1) {  ?>
			<div class="server" onclick="loadServer(superembed)">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php echo server_2_text; ?></div>
			</div>
			<?php } ?>
			<?php if ($fmovie_admin_server_3 == 1) {  ?>
			<div class="server" onclick="loadServer(vidsrc)">
				<span><?php echo esc_html__( 'Server', 'fmovie' ) ?></span>
				<div><?php echo server_3_text; ?></div>
			</div>
			<?php } ?>
			<?php endif; ?>
		</div>
		<!--/#servers-->
	<?php } ?>
	<div class="clearfix"></div>
</div>
<div class="clearfix"></div>
