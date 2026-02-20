<?php
/**
 * Template part for displaying slider wrapper
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

$fmovie_slider = get_option('admin_slider');
?>
<?php if ($fmovie_slider == 1) {  ?>
<div id="slider">
    <div class="swiper-wrapper">
        <?php 
            $query = new WP_Query( array(
            'post_type' => 'post',
            'category_name' => 'Slider',
            'showposts' => 6,
            'no_found_rows' => true
            ) );
            while ($query->have_posts()) : $query->the_post(); 
            get_template_part( 'template-parts/content/content', 'slider' );
            endwhile; 
            wp_reset_postdata(); 
        ?>
    </div>
    <div class="paging"></div>
</div>

<?php } else { ?>
<div style="margin-top:100px;"></div>
<?php } ?>
