<?php
/**
 * Template part for additional info on footer
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */
$color_style = get_option('admin_color_style');
$blog_info    = get_bloginfo( 'name' );
$footer_logo_class = 'logo_txt';
?>

<div class="about">
<div class="heading">About Us</div>
<div class="line"></div>
 

    <div>
        <p>
            <a href="/"><?php echo esc_html( $blog_info ); ?></a> is a free TV shows streaming website with zero ads. It allows you to <strong>watch TV shows online</strong>, <strong>watch TV shows online free</strong> in high quality for free. You can also download full TV shows and watch them later if you want.
        </p>
        <p class="small font-italic">
            This site does not store any files on our server; we only link to the media hosted on 3rd party services.
        </p>
        <div class="icons">
            <div>
                <div class="icon"><i class="fa fa-crown"></i></div>
                <span>High quality</span>
            </div>
            <div>
                <div class="icon"><i class="fa fa-play"></i></div>
                <span>Free forever</span>
            </div>
            <div>
                <div class="icon"><i class="fa fa-bolt"></i></div>
                <span>Fast load</span>
            </div>
            <div>
                <div class="icon"><i class="fa fa-closed-captioning"></i></div>
                <span>Multi subtitles</span>
            </div>
        </div>
    </div>

</div>


